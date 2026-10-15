package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import com.raxim.myscoutee.domain.integrations.document.IntegrationToken;
import com.raxim.myscoutee.domain.integrations.document.McpAuthorization;
import com.raxim.myscoutee.domain.integrations.document.McpOAuthClient;
import com.raxim.myscoutee.domain.integrations.repository.IntegrationTokensRepository;
import com.raxim.myscoutee.domain.integrations.repository.McpAuthorizationRepository;
import com.raxim.myscoutee.domain.integrations.repository.McpOAuthClientsRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.hash;
import java.time.Instant;
import java.util.*;
import java.net.URI;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.common.config.mongo.*;

import com.raxim.myscoutee.domain.integrations.dto.McpDtos;

@org.junit.jupiter.api.Tag("standard")
class McpAuthorizationServiceTest {
    @Test void blockedMainPackPreventsNewGrantsEvenForAFullClient() {
        var owner = users.findByUserId("owner").orElseThrow();
        owner.setIntegrationAccess(new IntegrationAccess(1, IntegrationAccess.Mode.BLOCKED, Map.of(id, IntegrationAccess.Mode.FULL)));
        assertThat(service.context("owner", request()).accessMode()).isEqualTo(IntegrationAccess.Mode.BLOCKED);
        assertThatThrownBy(() -> service.authorize("owner", new McpDtos.Consent(request(), true)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(grants);
    }
    final IntegrationTokensRepository tokens = mock(IntegrationTokensRepository.class);
    final McpAuthorizationRepository grants = mock(McpAuthorizationRepository.class);
    final UsersRepository users = mock(UsersRepository.class);
    final IntegrationPublicEndpointResolver endpoint = mock(IntegrationPublicEndpointResolver.class);
    final IntegrationTokenService keys = spy(new IntegrationTokenService(tokens,
            mock(MongoTemplate.class)));
    final McpOAuthClientsRepository registrations = mock(McpOAuthClientsRepository.class);
    final com.raxim.myscoutee.domain.platform.config.ConfigProperties config = new com.raxim.myscoutee.domain.platform.config.ConfigProperties();
    final ScheduledMongoSessionRunner databases = mock(ScheduledMongoSessionRunner.class);
    final McpAuthorizationService service = new McpAuthorizationService(keys,  tokens,  grants, new com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService(users),  Optional.empty(), endpoint, registrations, config, databases);
    final String id = UUID.randomUUID().toString(), verifier = "x".repeat(43);
    IntegrationToken client;
    @BeforeEach void setup() {
        when(endpoint.resolve()).thenReturn("https://app.example/api/integrations/v1");
        client = new IntegrationToken(); client.setId(id); client.setScope("mcp"); client.setOwnerUserId("owner");
        client.setName("Claude"); client.setRedirectUri("https://client.example/callback");
        client.setExpiresAtIso(Instant.now().plusSeconds(86400).toString());
        when(tokens.findById(id)).thenReturn(Optional.of(client));
        Users owner = new Users(); owner.setUserId("owner"); owner.setName("Owner");
        owner.setIntegrationAccess(new com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess(1, com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode.WRITE, Map.of()));
        when(users.findByUserId("owner")).thenReturn(Optional.of(owner));
    }
    @AfterEach void cleanup() { MongoSessionModeContext.clear(); }
    McpDtos.Authorization request(String callback, String resource, String challenge) {
        return new McpDtos.Authorization(id, callback, resource, "myscoutee:api", "code", challenge, "S256", "state +/?&");
    }
    McpDtos.Authorization request() { return request(client.getRedirectUri(), service.resource(), hash(verifier)); }
    Map<String,String> exchange(String code) {
        return new HashMap<>(Map.of("grant_type","authorization_code", "code",code,"code_verifier",verifier,
                "resource",service.resource(),"redirect_uri",client.getRedirectUri()));
    }
    void authenticated() {
        doReturn(new IntegrationTokenService.AuthenticatedIntegrationClient("owner",id,id))
                .when(keys).authenticateClient("secret",id,"ip","mcp");
    }
    @Test void metadataUsesCanonicalOriginAndOnlySupportedGrants() {
        assertThat(service.metadata()).containsEntry("issuer","https://app.example")
                .containsEntry("authorization_endpoint","https://app.example/mcp/authorize")
                .containsEntry("code_challenge_methods_supported",List.of("S256"));
        assertThat(service.metadata()).containsEntry("registration_endpoint", "https://app.example/api/mcp/oauth/register");
        assertThat(service.metadata()).doesNotContainKey("client_id_metadata_document_supported");
        assertThat(service.resourceMetadata()).containsEntry("resource","https://app.example/mcp/private");
    }
    @Test void consentBindsHashToExactClientResourcePkceAndNeverPersistsRawCode() {
        var redirect = service.authorize("owner", new McpDtos.Consent(request(),true));
        var captured = org.mockito.ArgumentCaptor.forClass(McpAuthorization.class);
        verify(grants).save(captured.capture()); var saved=captured.getValue();
        String code=URI.create(redirect.url()).getRawQuery().split("&")[0].substring(5);
        assertThat(code).startsWith("mcc_"); assertThat(saved.id).isEqualTo(hash(code));
        assertThat(saved.clientId).isEqualTo(id); assertThat(saved.ownerUserId).isEqualTo("owner");
        assertThat(saved.challenge).isEqualTo(hash(verifier)); assertThat(saved.resource).isEqualTo(service.resource());
        assertThat(saved.expiresAt).isBefore(Instant.now().plusSeconds(301));
        assertThat(redirect.url()).contains("&iss=https%3A%2F%2Fapp.example", "&state=state+%2B%2F%3F%26");
    }
    @Test void denialReturnsValidatedCallbackWithoutCreatingAuthorization() {
        assertThat(service.authorize("owner",new McpDtos.Consent(request(),false)).url()).contains("?error=access_denied&iss=");
        verifyNoInteractions(grants);
    }
    @Test void rejectsForeignAccountRedirectResourcePlainPkceAndDemoBeforePersisting() {
        Users other=new Users();other.setUserId("other");when(users.findByUserId("other")).thenReturn(Optional.of(other));
        assertThatThrownBy(()->service.authorize("other",new McpDtos.Consent(request(),true))).isInstanceOf(ResponseStatusException.class);
        for(var bad:List.of(request("https://attacker.example",service.resource(),hash(verifier)),
                request(client.getRedirectUri(),"https://elsewhere.example/mcp/private",hash(verifier)),
                request(client.getRedirectUri(),service.resource(),"bad"))) {
            assertThatThrownBy(()->service.authorize("owner",new McpDtos.Consent(bad,true))).isInstanceOf(ResponseStatusException.class);
        }
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        assertThatThrownBy(()->service.authorize("owner",new McpDtos.Consent(request(),true))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(grants);
    }
    @Test void exchangeConsumesCodeOnceAndSavesOnlyHashedCredentials() {
        authenticated(); McpAuthorization code = new McpAuthorization();code.ownerUserId="owner";code.clientId=id;
        when(grants.consumeCode(hash("code"),id,client.getRedirectUri(),service.resource(),hash(verifier))).thenReturn(code).thenReturn(null);
        var result=service.exchange(exchange("code"),id,"secret","ip");
        var captor=org.mockito.ArgumentCaptor.forClass(McpAuthorization.class);verify(grants).save(captor.capture());
        var saved=captor.getValue();
        assertThat(saved.accessHash).isEqualTo(hash((String)result.get("access_token")));
        assertThat(saved.refreshHash).isEqualTo(hash((String)result.get("refresh_token")));
        assertThat(saved.expiresAt).isEqualTo(Instant.parse(client.getExpiresAtIso()));
        assertThatThrownBy(()->service.exchange(exchange("code"),id,"secret","ip")).hasMessageContaining("invalid_grant");
        verify(grants,times(1)).save(any());
    }
    @Test void wrongClientScopeResourceOrVerifierCannotIssueGrant() {
        assertThatThrownBy(()->service.exchange(exchange("code"),id,"wrong","ip")).hasMessageContaining("invalid_client");
        authenticated(); var wrong=exchange("code");wrong.put("resource","https://other.example");
        assertThatThrownBy(()->service.exchange(wrong,id,"secret","ip")).hasMessageContaining("invalid_target");
        wrong.put("resource",service.resource());wrong.put("code_verifier","short");
        assertThatThrownBy(()->service.exchange(wrong,id,"secret","ip")).hasMessageContaining("invalid_grant");
        client.setScope("integration");
        assertThatThrownBy(()->service.exchange(exchange("code"),id,"secret","ip")).hasMessageContaining("invalid_client");
        verifyNoInteractions(grants);
    }
    @Test void refreshRotationAndReplayRevokeTheSameClientFamily() {
        authenticated(); var grant=new McpAuthorization();grant.clientId=id;grant.ownerUserId="owner";grant.expiresAt=Instant.now().plusSeconds(10000);
        String resource = service.resource();
        when(grants.rotate(eq(hash("old-refresh")),eq(id),eq(resource),anyString(),anyString())).thenReturn(grant).thenReturn(null);
        var input=Map.of("grant_type","refresh_token","refresh_token","old-refresh","resource",service.resource());
        var result=service.exchange(input,id,"secret","ip");
        assertThat((String)result.get("refresh_token")).startsWith("mcr_");
        assertThatThrownBy(()->service.exchange(input,id,"secret","ip")).hasMessageContaining("invalid_grant");
        verify(grants).revokeReplayedRefresh(hash("old-refresh"),id);
    }
    @Test void accessChecksActiveClientEveryTimeAndDoesNotAcceptClientSecretAsBearer() {
        var grant=new McpAuthorization();grant.clientId=id;grant.ownerUserId="owner";
        String access="mca_"+"a".repeat(43);
        when(grants.access(hash(access),service.resource())).thenReturn(grant);
        assertThat(service.authenticate(access).ownerUserId()).isEqualTo("owner");
        client.setRevokedAtIso(Instant.now().toString()); assertThat(service.authenticate(access)).isNull();
        assertThat(service.authenticate("msc_"+"a".repeat(43))).isNull();
    }
    McpDtos.Registration registration(List<String> redirects) {
        return new McpDtos.Registration("Inspector", redirects, "none", List.of("authorization_code", "refresh_token"), List.of("code"), "myscoutee:api");
    }
    String publicClient() {
        var registered = new McpOAuthClient(); registered.id = "mcp_" + UUID.randomUUID();
        registered.name = "Inspector"; registered.redirectUris = List.of(client.getRedirectUri());
        registered.expiresAt = Instant.now().plusSeconds(86400);
        when(registrations.findById(registered.id)).thenReturn(Optional.of(registered));
        return registered.id;
    }
    McpDtos.Authorization publicRequest(String publicId) {
        return new McpDtos.Authorization(publicId, client.getRedirectUri(), service.resource(), "myscoutee:api", "code", hash(verifier), "S256", "state");
    }
    @Test void registrationCreatesOnlyClientMetadataNotUserAuthority() {
        var result = service.register(registration(List.of("http://127.0.0.1:6274/oauth/callback")));
        assertThat(result).containsEntry("token_endpoint_auth_method", "none").doesNotContainKey("client_secret");
        assertThat(result.get("client_id").toString()).startsWith("mcp_");
        verify(registrations).insert(any(McpOAuthClient.class));
        verifyNoInteractions(tokens, grants, users);
        verify(keys, never()).create(anyString(), any(), anyString());
    }
    @Test void registrationRejectsUnsafeCallbacksUnsupportedMetadataAndFlooding() {
        for (String redirect : List.of("https://a/*", "http://evil.example/cb", "https://u@a/cb", "javascript:alert(1)"))
            assertThatThrownBy(() -> service.register(registration(List.of(redirect)))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.register(new McpDtos.Registration("x", List.of("https://a/cb"), "client_secret_post", null, null, null)))
                .hasMessageContaining("invalid_client_metadata");
        when(registrations.countByCreatedAtAfter(any())).thenReturn(60L);
        assertThatThrownBy(() -> service.register(registration(List.of("https://a/cb")))).hasMessageContaining("429");
        verify(registrations, never()).insert(any(McpOAuthClient.class));
    }
    @Test void publicConsentRequiresExplicitDisplayedProfileAndCancelCreatesNothing() {
        String publicId = publicClient();
        assertThat(service.context("owner", publicRequest(publicId)).profileId()).isEqualTo("owner");
        assertThat(service.authorize("owner", new McpDtos.Consent(publicRequest(publicId), false, "owner")).url()).contains("error=access_denied");
        assertThatThrownBy(() -> service.authorize("owner", new McpDtos.Consent(publicRequest(publicId), true))).hasMessageContaining("access_denied");
        var foreign = new Users(); foreign.setUserId("foreign");
        when(users.findByUserId("foreign")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.authorize("owner", new McpDtos.Consent(publicRequest(publicId), true, "foreign"))).hasMessageContaining("access_denied");
        verifyNoInteractions(grants);
        verify(keys, never()).create(anyString(), any(), anyString());
    }
    @Test void eventsReuseTheExistingManualGrantBindingAndRejectAnotherToken() {
        var grant = new McpAuthorization(); grant.id = "grant"; grant.kind = "grant";
        grant.ownerUserId = "owner"; grant.clientId = id;
        // Existing manual grants identify their key through clientId; tokenId may be absent.
        when(grants.activeGrant("grant", "owner", id, service.resource())).thenReturn(grant);
        assertThat(service.activeSubscriptionGrant("grant", "owner", id, id)).isTrue();
        assertThat(service.activeSubscriptionGrant("grant", "owner", "foreign-token", id)).isFalse();
        client.setRevokedAtIso(Instant.now().toString());
        assertThat(service.activeSubscriptionGrant("grant", "owner", id, id)).isFalse();
    }
    @Test void sharedDatabaseWorkerAcceptsDemoGrantWithoutRelaxingRequestRealmOrRevocation() {
        config.setMcpDemoEnabled(true);
        var grant = new McpAuthorization(); grant.id = "grant"; grant.kind = "grant";
        grant.ownerUserId = "owner"; grant.clientId = id;
        when(grants.activeGrant("grant", "owner", id, service.resource())).thenReturn(grant);
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        new ScheduledMongoSessionRunner("demo_db", "demo_db").runAcrossConfiguredDatabases(() -> {
            assertThat(MongoSessionModeContext.currentMode()).isEqualTo(MongoSessionMode.REAL);
            assertThat(service.activeSubscriptionGrant("grant", "owner", id, id)).isFalse();
            assertThatThrownBy(() -> service.context("owner", request())).hasMessageContaining("access_denied");
            assertThat(service.activeDeliveryGrant("grant", "owner", id, id)).isTrue();
            assertThat(service.activeDeliveryGrant("grant", "owner", "foreign-token", id)).isFalse();
            client.setRevokedAtIso(Instant.now().toString());
            assertThat(service.activeDeliveryGrant("grant", "owner", id, id)).isFalse();
        });
        assertThat(MongoSessionModeContext.currentMode()).isEqualTo(MongoSessionMode.DEMO);
    }
    @Test void distinctDatabaseWorkersCannotUseGrantsOutsideTheConfiguredMcpRealm() {
        when(databases.hasDistinctDemoDatabase()).thenReturn(true);
        var grant = new McpAuthorization(); grant.id = "grant"; grant.kind = "grant";
        grant.ownerUserId = "owner"; grant.clientId = id;
        when(grants.activeGrant("grant", "owner", id, service.resource())).thenReturn(grant);
        for (boolean demo : List.of(false, true)) {
            config.setMcpDemoEnabled(demo);
            for (MongoSessionMode mode : MongoSessionMode.values()) {
                MongoSessionModeContext.set(mode);
                assertThat(service.activeDeliveryGrant("grant", "owner", id, id))
                        .as("demo enabled %s, worker %s", demo, mode)
                        .isEqualTo(mode == (demo ? MongoSessionMode.DEMO : MongoSessionMode.REAL));
            }
        }
    }
    @Test void sharedDcrRegistrationKeepsTwoUnrelatedUsersGrantsAndRevocationIndependent() {
        String publicId = publicClient();
        client.setOauthClientId(publicId);
        var second = new IntegrationToken(); second.setId("bob-token"); second.setOwnerUserId("bob");
        second.setScope("mcp"); second.setOauthClientId(publicId); second.setExpiresAtIso(client.getExpiresAtIso());
        var bob = new Users(); bob.setUserId("bob"); bob.setIntegrationAccess(new com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess(1, com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode.WRITE, Map.of()));
        when(users.findByUserId("bob")).thenReturn(Optional.of(bob));
        when(tokens.findByOwnerUserIdOrderByCreatedAtIsoDesc("owner")).thenReturn(List.of(client));
        when(tokens.findByOwnerUserIdOrderByCreatedAtIsoDesc("bob")).thenReturn(List.of(second));
        service.authorize("owner", new McpDtos.Consent(publicRequest(publicId), true, "owner"));
        service.authorize("bob", new McpDtos.Consent(publicRequest(publicId), true, "bob"));
        var captured = org.mockito.ArgumentCaptor.forClass(McpAuthorization.class);
        verify(grants, times(2)).save(captured.capture());
        assertThat(captured.getAllValues()).extracting(g -> g.clientId).containsExactly(publicId, publicId);
        assertThat(captured.getAllValues()).extracting(g -> g.ownerUserId).containsExactly("owner", "bob");
        assertThat(captured.getAllValues()).extracting(g -> g.tokenId).containsExactly(id, "bob-token");
        verify(keys, never()).create(anyString(), any(), anyString());
    }
    @Test void publicCodeUsesPkceAndOwnerConnectionWithoutASecretAndRevocationWins() {
        String publicId = publicClient(); client.setOauthClientId(publicId);
        var code = new McpAuthorization(); code.clientId=publicId; code.tokenId=id; code.ownerUserId="owner";
        when(grants.consumeCode(hash("code"), publicId, client.getRedirectUri(), service.resource(), hash(verifier))).thenReturn(code).thenReturn(null);
        var input = exchange("code");
        assertThatThrownBy(() -> service.exchange(input, publicId, "invented", "ip")).hasMessageContaining("invalid_client");
        var result = service.exchange(input, publicId, null, "ip");
        var saved = org.mockito.ArgumentCaptor.forClass(McpAuthorization.class); verify(grants).save(saved.capture());
        assertThat(saved.getValue().ownerUserId).isEqualTo("owner");
        assertThat(saved.getValue().tokenId).isEqualTo(id);
        when(grants.access(hash((String)result.get("access_token")), service.resource())).thenReturn(saved.getValue());
        assertThat(service.authenticate((String)result.get("access_token")).tokenId()).isEqualTo(id);
        client.setRevokedAtIso(Instant.now().toString());
        assertThat(service.authenticate((String)result.get("access_token"))).isNull();
        assertThatThrownBy(() -> service.exchange(input, publicId, null, "ip")).hasMessageContaining("invalid_grant");
        verify(keys, never()).authenticateClient(any(), any(), any(), any());
    }
    @Test void reconnectReusesOnlyThisOwnersActiveConnection() {
        String publicId = publicClient(); client.setOauthClientId(publicId);
        when(tokens.findByOwnerUserIdOrderByCreatedAtIsoDesc("owner")).thenReturn(List.of(client));
        service.authorize("owner", new McpDtos.Consent(publicRequest(publicId), true, "owner"));
        var saved = org.mockito.ArgumentCaptor.forClass(McpAuthorization.class); verify(grants).save(saved.capture());
        assertThat(saved.getValue().tokenId).isEqualTo(id);
        verify(keys, never()).create(anyString(), any(), anyString());
    }
    @Test void demoConsentRequiresExplicitDevConfigurationAndCannotCrossIntoRealRealm() {
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        assertThatThrownBy(() -> service.context("owner", request())).hasMessageContaining("access_denied");
        config.setMcpDemoEnabled(true);
        assertThat(service.context("owner", request()).profileId()).isEqualTo("owner");
        MongoSessionModeContext.set(MongoSessionMode.REAL);
        assertThatThrownBy(() -> service.context("owner", request())).hasMessageContaining("access_denied");
        config.setFirebaseEnabled(true);
        assertThat(service.context("owner", request()).profileId()).isEqualTo("owner");
    }
    @Test void generatesManualCredentialsWithoutNameOrCallbackForm() {
        var stored = new java.util.concurrent.atomic.AtomicReference<IntegrationToken>();
        when(tokens.findByOwnerUserIdOrderByCreatedAtIsoDesc("owner")).thenReturn(List.of());
        when(tokens.save(any())).thenAnswer(invocation -> { var token = invocation.<IntegrationToken>getArgument(0); stored.set(token); return token; });
        when(tokens.findByIdAndOwnerUserId(anyString(), eq("owner"))).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        var created = service.create("owner", new McpDtos.ClientRequest("Client 1", null));
        assertThat(created.client().manual()).isTrue();
        assertThat(created.client().clientId()).isEqualTo(stored.get().getId());
        assertThat(created.client().redirectUri()).isEmpty();
        assertThat(created.secret()).startsWith("msc_");
        assertThat(stored.get().getTokenHash()).isEqualTo(hash(created.secret()));
        when(tokens.findByOwnerUserIdOrderByCreatedAtIsoDesc("owner")).thenReturn(List.of(stored.get()));
        assertThat(service.settings("owner").clients()).hasSize(1);
    }
    @Test void generatedManualClientUsesOnlyExactDeploymentCallbacks() {
        client.setRedirectUri("");
        config.setMcpManualRedirectUris(List.of("https://client.example/callback"));
        var allowed = request("https://client.example/callback", service.resource(), hash(verifier));
        assertThat(service.context("owner", allowed).profileId()).isEqualTo("owner");
        assertThatThrownBy(() -> service.context("owner", request("https://attacker.example/callback", service.resource(), hash(verifier))))
                .hasMessageContaining("invalid_request");
        assertThatThrownBy(() -> service.context("owner", request("https://client.example/callback?other=1", service.resource(), hash(verifier))))
                .hasMessageContaining("invalid_request");
        config.setMcpManualRedirectUris(List.of());
        assertThatThrownBy(() -> service.context("owner", allowed)).hasMessageContaining("invalid_request");
        verifyNoInteractions(grants);
    }
    @Test void rejectsUnsafeCallbacksWithoutThrowingNullPointer() {
        for(String url:List.of("http:/missing","http://example.com/callback","https://x@a/cb","https://a/*","https://a/cb#f","javascript:alert(1)"))
            assertThatThrownBy(()->McpAuthorizationService.redirectUri(url)).isInstanceOf(ResponseStatusException.class);
        assertThat(McpAuthorizationService.redirectUri("http://127.0.0.1:4352/callback")).contains("4352");
    }
    @Test void absentMcpPreferenceIsBlockedWithoutGeneratingAConnection() {
        var owner = new Users(); owner.setUserId("owner");
        when(users.findByUserId("owner")).thenReturn(Optional.of(owner));
        assertThat(service.context("owner", request()).accessMode()).isEqualTo(com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode.BLOCKED);
        assertThatThrownBy(() -> service.authorize("owner", new McpDtos.Consent(request(), true, "owner")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(keys, never()).create(anyString(), any(), anyString());
        verify(grants, never()).save(any());
    }

    @Test void personalConsentStartsWithoutWorkspaceServicesAndKeepsExistingAccountReads() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean(IntegrationTokenService.class, () -> keys);
            context.registerBean(IntegrationTokensRepository.class, () -> tokens);
            context.registerBean(McpAuthorizationRepository.class, () -> grants);
            context.registerBean(com.raxim.myscoutee.domain.accounts.api.ProfileDirectory.class,
                    () -> new com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService(users));
            context.registerBean(MongoTemplate.class, () -> mock(MongoTemplate.class));
            context.registerBean(IntegrationPublicEndpointResolver.class, () -> endpoint);
            context.registerBean(McpOAuthClientsRepository.class, () -> registrations);
            context.registerBean(com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration.class, () -> config);
            context.registerBean(ScheduledMongoSessionRunner.class, () -> databases);
            context.register(McpAuthorizationService.class);
            context.refresh();
            var personal = context.getBean(McpAuthorizationService.class);
            assertThat(personal.context("owner", request()).groupName()).isNull();
            verify(users, times(4)).findByUserId("owner");
            clearInvocations(users);
            assertThat(personal.authorize("owner", new McpDtos.Consent(request(), true)).url()).contains("?code=mcc_");
            verify(users, times(4)).findByUserId("owner");
            verify(grants).save(any());

            var owner = users.findByUserId("owner").orElseThrow();
            owner.setWorkspaceGroupId("workspace");
            clearInvocations(grants);
            assertThatThrownBy(() -> personal.authorize("owner", new McpDtos.Consent(request(), true)))
                    .hasMessageContaining("access_denied");
            verifyNoInteractions(grants);
        }
    }

}
