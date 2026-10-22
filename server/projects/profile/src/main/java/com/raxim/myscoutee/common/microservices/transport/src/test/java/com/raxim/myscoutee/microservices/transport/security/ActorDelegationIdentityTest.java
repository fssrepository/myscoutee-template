package com.raxim.myscoutee.microservices.transport.security;

import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.accounts.api.AccountIdentity;
import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActorDelegationIdentityTest {
    private static final String AUDIENCE = "accounts:selected-profile:v1";
    private static final String PATH = "/_internal/v1/accounts/selected-profile";
    private static final byte[] BODY = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);
    private static ActorDelegation.Issuer issuer;
    private static ActorDelegation.Verifier verifier;
    private static String publicKey;

    @BeforeAll static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        issuer = new ActorDelegation.Issuer(Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()), publicKey, CLOCK);
        verifier = new ActorDelegation.Verifier(publicKey, CLOCK);
    }

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        MongoSessionModeContext.clear();
    }

    private void authenticate(String accountId, String workspace) {
        var profile = mock(WorkspaceProfile.class);
        when(profile.getUserId()).thenReturn("selected");
        when(profile.getAccountUserId()).thenReturn(accountId);
        when(profile.getWorkspaceGroupId()).thenReturn(workspace);
        var principal = mock(AccountIdentity.class);
        when(principal.getUserId()).thenReturn("selected");
        when(principal.getEmail()).thenReturn("actor@example.invalid");
        when(principal.getUser()).thenReturn(profile);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("A"))));
    }

    private String token() { return issuer.issueForCurrent(AUDIENCE, "POST", PATH, BODY); }

    @Test void carriesSelectedIdentityAndActualAuthoritiesWithoutAccountDocumentOrCredentials() {
        authenticate("account", "workspace");
        var principal = verifier.verifyPrincipal(token(), AUDIENCE, "POST", PATH, BODY);
        assertEquals("selected", principal.getUserId());
        assertEquals("account", principal.getProfileIdentity().accountUserId());
        assertEquals("workspace", principal.getProfileIdentity().workspaceGroupId());
        assertEquals("actor@example.invalid", principal.getEmail());
        assertEquals(List.of(new SimpleGrantedAuthority("A")), principal.getAuthorities());
        assertFalse(AccountIdentity.class.isInstance(principal));
    }

    @Test void preservesAbsentAndExplicitlyEmptyWorkspaceSeparately() {
        authenticate(null, null);
        var missing = verifier.verifyPrincipal(token(), AUDIENCE, "POST", PATH, BODY);
        assertNull(missing.getProfileIdentity().accountUserId());
        assertNull(missing.getProfileIdentity().workspaceGroupId());
        authenticate("", "");
        var empty = verifier.verifyPrincipal(token(), AUDIENCE, "POST", PATH, BODY);
        assertEquals("", empty.getProfileIdentity().accountUserId());
        assertEquals("", empty.getProfileIdentity().workspaceGroupId());
    }

    @Test void rejectsWrongOperationBodyTenantAndExpirationBeforeInstallingAuthority() {
        authenticate("account", "workspace");
        String token = token();
        assertThrows(AccessDeniedException.class, () -> verifier.verifyPrincipal(token, "another-owner", "POST", PATH, BODY));
        assertThrows(AccessDeniedException.class, () -> verifier.verifyPrincipal(token, AUDIENCE, "GET", PATH, BODY));
        assertThrows(AccessDeniedException.class, () -> verifier.verifyPrincipal(token, AUDIENCE, "POST", PATH + "/other", BODY));
        assertThrows(AccessDeniedException.class, () -> verifier.verifyPrincipal(token, AUDIENCE, "POST", PATH, new byte[0]));
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        assertThrows(AccessDeniedException.class, () -> verifier.verifyPrincipal(token, AUDIENCE, "POST", PATH, BODY));
        MongoSessionModeContext.clear();
        var expired = new ActorDelegation.Verifier(publicKey, Clock.offset(CLOCK, java.time.Duration.ofSeconds(30)));
        assertThrows(AccessDeniedException.class, () -> expired.verifyPrincipal(token, AUDIENCE, "POST", PATH, BODY));
    }

    @Test void restoresServiceContextAfterNestedOwnerFailure() {
        authenticate("account", "workspace");
        var principal = verifier.verifyPrincipal(token(), AUDIENCE, "POST", PATH, BODY);
        var original = SecurityContextHolder.createEmptyContext();
        original.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("internal-reader", null,
                List.of(new SimpleGrantedAuthority("accounts:read"))));
        SecurityContextHolder.setContext(original);
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = DelegatedActorContext.open(principal)) {
                assertSame(principal, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
                assertNull(SecurityContextHolder.getContext().getAuthentication().getCredentials());
                throw new IllegalStateException("owner failed");
            }
        });
        assertSame(original, SecurityContextHolder.getContext());
        assertEquals("internal-reader", original.getAuthentication().getPrincipal());
    }

    @Test void serviceCredentialAloneCannotIssueActorDelegation() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "internal-reader", null, List.of(new SimpleGrantedAuthority("accounts:read"))));
        assertThrows(AccessDeniedException.class, this::token);
    }

    @Test void verifiedPrincipalCannotBeReusedAfterSwitchingDatabaseContext() {
        authenticate("account", "workspace");
        var principal = verifier.verifyPrincipal(token(), AUDIENCE, "POST", PATH, BODY);
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        assertThrows(AccessDeniedException.class, () -> DelegatedActorContext.open(principal));
        assertThrows(AccessDeniedException.class, () -> issuer.issueDerived(principal, AUDIENCE, "POST", PATH, ActorDelegation.digest(BODY)));
    }
}
