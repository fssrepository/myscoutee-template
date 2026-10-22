package com.raxim.myscoutee.microservices.transport.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.accounts.api.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class SocketAuthorizationTest {
    static final String TARGET = "/activities/chats/ws?chatId=chat&sessionKind=demo";
    static final ObjectMapper MAPPER = new com.raxim.myscoutee.common.config.JsonConfig().objectMapper();
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    TestClock clock; ActorDelegation.Issuer issuer; ActorDelegation.Verifier verifier;
    RemotePublicAuthorization owner;
    RemotePublicAuthorization.Request request;

    @BeforeEach void setup() throws Exception {
        clock = new TestClock();
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        issuer = new ActorDelegation.Issuer(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKey, clock);
        verifier = new ActorDelegation.Verifier(publicKey, clock);
        owner = mock(RemotePublicAuthorization.class);
        request = new RemotePublicAuthorization.Request("GET", TARGET, "127.0.0.1", Map.of());
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); MongoSessionModeContext.clear(); }

    String grant(String user, String nonce) {
        var profile = mock(WorkspaceProfile.class); when(profile.getUserId()).thenReturn(user);
        var account = mock(AccountIdentity.class); when(account.getUserId()).thenReturn(user);
        when(account.getUser()).thenReturn(profile); when(account.getEmail()).thenReturn("actor@example.invalid");
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(account, null, List.of()));
        SecurityContextHolder.setContext(context);
        try { return issuer.issueForCurrent("assembly:public:communication", "GET", TARGET, nonce.getBytes()); }
        finally { SecurityContextHolder.setContext(previous); }
    }
    SocketAuthorization socket() {
        var actor = verifier.verifyPrincipal(grant("member", "initial"), "assembly:public:communication", "GET", TARGET, "initial".getBytes());
        return new SocketAuthorization(owner, request, verifier, MAPPER, "communication", MongoSessionMode.DEMO,
                UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities()), clock);
    }
    @SuppressWarnings("unchecked") HttpResponse<byte[]> reply(int status, String mode, String actor) {
        var response = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (a,b)->true));
        when(response.body()).thenReturn(MAPPER.writeValueAsBytes(new PublicAuthorizationProtocol.Result(mode, actor, null)));
        return response;
    }
    @Test void messageBurstsReuseTheGrantAndRenewThroughTheAccountOwnerBeforeExpiry() {
        var socket = socket(); var initial = socket.authentication();
        for (int i=0;i<100;i++) socket.refreshIfNeeded();
        verifyNoInteractions(owner); assertSame(initial, socket.authentication());
        when(owner.authorize(eq(request), anyString())).thenAnswer(call -> reply(200, "DEMO", grant("member", call.getArgument(1))));
        clock.now = clock.now.plusSeconds(26);
        MongoSessionModeContext.set(MongoSessionMode.REAL);
        // The mock issuer uses the same selected realm as the actual owner.
        when(owner.authorize(eq(request), anyString())).thenAnswer(call -> {
            var previous = MongoSessionModeContext.currentMode(); MongoSessionModeContext.set(MongoSessionMode.DEMO);
            try { return reply(200, "DEMO", grant("member", call.getArgument(1))); }
            finally { MongoSessionModeContext.set(previous); }
        });
        socket.refreshIfNeeded();
        assertNotSame(initial, socket.authentication()); assertEquals(MongoSessionMode.REAL, MongoSessionModeContext.currentMode());
        clock.now = clock.now.plusSeconds(10);
        for (int i=0;i<100;i++) socket.refreshIfNeeded();
        verify(owner, times(1)).authorize(eq(request), anyString());
    }
    @Test void revokedSessionCannotRenewAnExpiredGrant() {
        var socket = socket(); var initial = socket.authentication(); clock.now = clock.now.plusSeconds(31);
        var rejected = reply(401, "DEMO", null);
        when(owner.authorize(eq(request), anyString())).thenReturn(rejected);
        assertThrows(RuntimeException.class, socket::refreshIfNeeded); assertSame(initial, socket.authentication());
    }
    @Test void changedActorModeOrNonceCannotTakeOverTheSocket() {
        for (String failure : List.of("actor", "mode", "nonce", "anonymous")) {
            var socket = socket(); var initial = socket.authentication(); clock.now = clock.now.plusSeconds(26);
            when(owner.authorize(eq(request), anyString())).thenAnswer(call -> reply(200,
                    failure.equals("mode") ? "REAL" : "DEMO",
                    failure.equals("anonymous") ? null : grant(failure.equals("actor") ? "other" : "member",
                            failure.equals("nonce") ? "wrong" : call.getArgument(1))));
            assertThrows(RuntimeException.class, socket::refreshIfNeeded, failure);
            assertSame(initial, socket.authentication()); assertEquals(MongoSessionMode.DEMO, MongoSessionModeContext.currentMode());
        }
    }
    @Test void snapshotsOnlyBoundedAuthenticationInputAndRedactsIt() {
        var servlet = new MockHttpServletRequest("GET", "/activities/chats/ws"); servlet.setRemoteAddr("127.0.0.1");
        servlet.setQueryString("token=private"); servlet.addHeader("Authorization", "private"); servlet.setContent(new byte[100]);
        var saved = RemotePublicAuthorization.snapshot(servlet);
        servlet.setQueryString("changed"); servlet.removeHeader("Authorization");
        assertEquals("/activities/chats/ws?token=private", saved.target());
        assertEquals(List.of("private"), saved.headers().get("Authorization"));
        assertFalse(saved.toString().contains("private"));
        servlet.addHeader("Authorization", "x".repeat(32769));
        assertThrows(IllegalArgumentException.class, () -> RemotePublicAuthorization.snapshot(servlet));
    }
}
