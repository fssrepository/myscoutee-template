package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import com.raxim.myscoutee.domain.integrations.document.IntegrationWebhookDelivery;
import com.raxim.myscoutee.domain.integrations.document.McpEventSubscription;
import com.raxim.myscoutee.domain.integrations.repository.McpEventSubscriptionsRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.raxim.myscoutee.domain.integrations.dto.McpEventDtos.*;

import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.AuthenticatedIntegrationClient;
import com.raxim.myscoutee.domain.platform.service.DeploymentConfigurationCrypto;
import tools.jackson.databind.json.JsonMapper;

@org.junit.jupiter.api.Tag("standard")
class McpEventsServiceTest {
    final McpEventSubscriptionsRepository subscriptions = mock(McpEventSubscriptionsRepository.class);
    final UsersRepository users = mock(UsersRepository.class);
    final McpAuthorizationService oauth = mock(McpAuthorizationService.class);
    final DeploymentConfigurationCrypto crypto = mock(DeploymentConfigurationCrypto.class);
    final McpEventCallbackClient callbacks = mock(McpEventCallbackClient.class);
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final JsonMapper json = JsonMapper.builder().build();
    final IntegrationRoleAccessService roles = mock(IntegrationRoleAccessService.class);
    final McpEventsService service = new McpEventsService(subscriptions, new com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService(users),  Optional.empty(),  oauth,  crypto,  callbacks,  json,  mongo,  roles);
    final AuthenticatedIntegrationClient client = new AuthenticatedIntegrationClient("alice", "token-a", "shared-chatgpt",
            "mcp", IntegrationAccess.Mode.FULL, "grant-a");
    final String secret = "whsec_" + Base64.getEncoder().encodeToString(new byte[32]);
    Users owner;
    Request request(Long ttl) { return new Request("operator.attention", Map.of("resourceId", "node"),
            new Delivery("webhook", "https://callback.example/chat-a", secret), ttl, null); }

    @BeforeEach void setup() throws Exception {
        owner = new Users(); owner.setUserId("alice");
        when(roles.operator(owner)).thenReturn(true);
        when(roles.privileged(owner)).thenReturn(true);
        owner.setIntegrationAccess(new IntegrationAccess(1, IntegrationAccess.Mode.FULL, Map.of("token-a", IntegrationAccess.Mode.FULL)));
        when(users.findByUserId("alice")).thenReturn(Optional.of(owner));
        when(oauth.activeSubscriptionGrant("grant-a", "alice", "token-a", "shared-chatgpt")).thenReturn(true);
        when(oauth.activeDeliveryGrant("grant-a", "alice", "token-a", "shared-chatgpt")).thenReturn(true);
        when(crypto.encrypt(anyString(), anyString())).thenReturn(new EncryptedSecret());
        when(subscriptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(callbacks.send(anyString(), anyString(), anyString(), any(), anyString(), isNull(), eq(true)))
                .thenAnswer(invocation -> new McpEventCallbackClient.Response(200,
                        json.writeValueAsBytes(Map.of("challenge", json.readTree((byte[]) invocation.getArgument(3)).path("challenge").asText()))));
    }

    @Test @org.junit.jupiter.api.Timeout(10)
    void concurrentVerifiedSubscriptionsCannotBothTakeTheLastProfileSlot() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger(99);
        var verified = new java.util.concurrent.CyclicBarrier(2);
        when(subscriptions.countByOwnerUserIdAndEnabledTrueAndExpiresAtAfter(eq("alice"), any()))
                .thenAnswer(invocation -> (long) count.get());
        when(subscriptions.save(any())).thenAnswer(invocation -> {
            count.incrementAndGet(); return invocation.getArgument(0);
        });
        when(callbacks.send(anyString(), anyString(), anyString(), any(), anyString(), isNull(), eq(true)))
                .thenAnswer(invocation -> {
                    verified.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    return new McpEventCallbackClient.Response(200, json.writeValueAsBytes(Map.of("challenge",
                            json.readTree((byte[]) invocation.getArgument(3)).path("challenge").asText())));
                });
        var second = new Request(request(null).name(), request(null).arguments(),
                new Delivery("webhook", "https://callback.example/chat-b", secret), null, null);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = workers.invokeAll(List.<java.util.concurrent.Callable<Boolean>>of(
                    () -> admitted(request(null)), () -> admitted(second)));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(count.get()).isEqualTo(100);
        verify(subscriptions, times(1)).save(any());
    }

    private boolean admitted(Request input) {
        try { service.subscribe(client, input); return true; }
        catch (McpEventsService.ProtocolException error) {
            assertThat(error.failure.code()).isEqualTo(-32013);
            assertThat(error.failure.data()).containsEntry("limit", "subscriptions");
            return false;
        }
    }

    @Test void twoUsersOfTheSameRegisteredClientHaveDifferentSubscriptionIdentities() {
        var bob = new AuthenticatedIntegrationClient("bob", "token-b", "shared-chatgpt", "mcp", IntegrationAccess.Mode.FULL, "grant-b");
        assertThat(McpEventsService.identity(client, request(null))).isNotEqualTo(McpEventsService.identity(bob, request(null)));
        assertThat(McpEventsService.identity(client, request(null))).isEqualTo(McpEventsService.identity(client, request(60000L)));
        var newSecret = new Request(request(null).name(), request(null).arguments(),
                new Delivery("webhook", request(null).delivery().url(), "replacement"), null, null);
        assertThat(McpEventsService.identity(client, newSecret)).isEqualTo(McpEventsService.identity(client, request(null)));
    }
    @Test void verificationPrecedesActivationAndTtlNeverExceedsRequestedDuration() throws Exception {
        Instant before = Instant.now();
        Subscribed result = service.subscribe(client, request(60000L));
        var saved = org.mockito.ArgumentCaptor.forClass(McpEventSubscription.class);
        var order = inOrder(callbacks, subscriptions);
        order.verify(callbacks).send(anyString(), eq(result.id()), startsWith("msg_verification_"), any(), eq(secret), isNull(), eq(true));
        order.verify(subscriptions).save(saved.capture());
        assertThat(saved.getValue().ownerUserId).isEqualTo("alice");
        assertThat(saved.getValue().grantId).isEqualTo("grant-a");
        assertThat(saved.getValue().secret).isNotNull();
        assertThat(Instant.parse(result.refreshBefore())).isBetween(before.plusSeconds(60), Instant.now().plusSeconds(60));
        assertThat(result.cursor()).isNull();
    }
    @Test void failedChallengeCannotPersistOrActivateAnything() throws Exception {
        when(callbacks.send(anyString(), anyString(), anyString(), any(), anyString(), isNull(), eq(true)))
                .thenReturn(new McpEventCallbackClient.Response(200, "{\"challenge\":\"wrong\"}".getBytes()));
        assertThatThrownBy(() -> service.subscribe(client, request(null))).isInstanceOf(McpEventsService.ProtocolException.class)
                .satisfies(error -> assertThat(((McpEventsService.ProtocolException) error).failure.code()).isEqualTo(-32015));
        verify(subscriptions, never()).save(any());
    }
    @Test void writePackCannotCreateBackgroundReadsAndRevocationDuringVerificationWins() throws Exception {
        owner.setIntegrationAccess(IntegrationAccess.initial());
        assertThatThrownBy(() -> service.subscribe(client, request(null))).isInstanceOf(McpEventsService.ProtocolException.class);
        verifyNoInteractions(callbacks);
        owner.setIntegrationAccess(new IntegrationAccess(2, IntegrationAccess.Mode.FULL, Map.of("token-a", IntegrationAccess.Mode.FULL)));
        when(oauth.activeSubscriptionGrant("grant-a", "alice", "token-a", "shared-chatgpt")).thenReturn(true, false);
        assertThatThrownBy(() -> service.subscribe(client, request(null))).isInstanceOf(McpEventsService.ProtocolException.class);
        verify(subscriptions, never()).save(any());
    }
    @Test void refreshRotatesKeysAndRestartedServiceUsesPersistedVerification() throws Exception {
        var existing = new McpEventSubscription(); existing.id = McpEventsService.identity(client, request(null));
        existing.enabled = true; existing.grantId = "grant-a"; existing.generation = "generation"; existing.expiresAt = Instant.now().plusSeconds(3600);
        existing.secretHash = "old"; existing.secret = new EncryptedSecret();
        existing.verifiedUntil = Instant.now().plusSeconds(120);
        when(subscriptions.findById(existing.id)).thenReturn(Optional.of(existing));
        when(subscriptions.findFirstByOwnerUserIdAndTokenIdAndClientIdAndCallbackUrlAndSecretHashAndVerifiedUntilAfter(
                anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(Optional.of(existing));
        service.subscribe(client, request(null));
        assertThat(existing.generation).isEqualTo("generation");
        assertThat(existing.previousSecret).isNotNull();
        assertThat(existing.previousSecretUntil).isAfter(Instant.now());
        verifyNoInteractions(callbacks);
    }
    @Test void unsubscribeIsIdempotentAndOldGenerationCannotDeliverAfterResubscription() throws Exception {
        var existing = new McpEventSubscription(); existing.id = McpEventsService.identity(client, request(null));
        existing.enabled = true; existing.expiresAt = Instant.now().plusSeconds(3600); existing.generation = "old";
        when(subscriptions.findById(existing.id)).thenReturn(Optional.of(existing));
        service.unsubscribe(client, request(null)); service.unsubscribe(client, request(null));
        assertThat(existing.enabled).isFalse(); assertThat(existing.secret).isNull();
        existing.enabled = true;
        var delivery = new IntegrationWebhookDelivery(); delivery.setSubscriptionId(existing.id); delivery.setSubscriptionGeneration("old");
        assertThat(service.deliver(delivery)).isEqualTo(410);
        verifyNoInteractions(callbacks);
    }
    @Test void expiredSubscriptionAndRevokedGrantStopAlreadyQueuedData() throws Exception {
        var sub = new McpEventSubscription(); sub.id = "sub"; sub.enabled = true; sub.expiresAt = Instant.now().minusSeconds(1);
        sub.generation = "g"; sub.ownerUserId = "alice"; sub.tokenId = "token-a"; sub.clientId = "shared-chatgpt";
        sub.grantId = "grant-a"; sub.name = "operator.attention"; sub.resourceId = "node";
        when(subscriptions.findById("sub")).thenReturn(Optional.of(sub));
        var delivery = new IntegrationWebhookDelivery(); delivery.setSubscriptionId("sub"); delivery.setSubscriptionGeneration("g");
        delivery.setOwnerUserId("alice"); delivery.setEventType("operator.attention"); delivery.setResourceExternalId("node");
        assertThat(service.deliver(delivery)).isEqualTo(410);
        sub.expiresAt = Instant.now().plusSeconds(3600);
        when(oauth.activeDeliveryGrant("grant-a", "alice", "token-a", "shared-chatgpt")).thenReturn(false);
        assertThat(service.deliver(delivery)).isEqualTo(410);
        verifyNoInteractions(callbacks);
    }
    @Test void backgroundDeliveryUsesItsGrantCheckAndStillRequiresFullAccess() throws Exception {
        var sub = new McpEventSubscription(); sub.id = "sub"; sub.enabled = true;
        sub.expiresAt = Instant.now().plusSeconds(3600); sub.generation = "g";
        sub.ownerUserId = "alice"; sub.tokenId = "token-a"; sub.clientId = "shared-chatgpt";
        sub.grantId = "grant-a"; sub.name = "operator.attention"; sub.resourceId = "node";
        sub.callbackUrl = "https://callback.example/chat-a"; sub.secret = new EncryptedSecret();
        when(subscriptions.findById("sub")).thenReturn(Optional.of(sub));
        when(oauth.activeSubscriptionGrant("grant-a", "alice", "token-a", "shared-chatgpt")).thenReturn(false);
        when(crypto.decrypt("mcp-events/sub", sub.secret)).thenReturn(secret);
        when(callbacks.send(eq(sub.callbackUrl), eq("sub"), eq("event-id"), any(), eq(secret), isNull(), eq(false), isNull()))
                .thenReturn(new McpEventCallbackClient.Response(200, new byte[0]));
        var delivery = new IntegrationWebhookDelivery(); delivery.setId("event-id");
        delivery.setSubscriptionId("sub"); delivery.setSubscriptionGeneration("g");
        delivery.setOwnerUserId("alice"); delivery.setEventType("operator.attention");
        delivery.setResourceExternalId("node"); delivery.setPayloadJson("{}");
        assertThat(service.deliver(delivery)).isEqualTo(200);
        verify(oauth).activeDeliveryGrant("grant-a", "alice", "token-a", "shared-chatgpt");
        verify(oauth, never()).activeSubscriptionGrant(anyString(), anyString(), anyString(), anyString());
        owner.setIntegrationAccess(IntegrationAccess.initial());
        assertThat(service.deliver(delivery)).isEqualTo(410);
        verify(callbacks).send(eq(sub.callbackUrl), eq("sub"), eq("event-id"), any(), eq(secret), isNull(), eq(false), isNull());
        verifyNoMoreInteractions(callbacks);
    }
    @Test void readPackCannotSubscribeAndRoleRevocationStopsDelivery() throws Exception {
        var read = client.withAccess(IntegrationAccess.Mode.READ);
        assertThat(service.catalog(read)).containsEntry("events", List.of());
        owner.setIntegrationAccess(new IntegrationAccess(2, IntegrationAccess.Mode.READ, Map.of("token-a", IntegrationAccess.Mode.FULL)));
        assertThatThrownBy(() -> service.subscribe(read, request(null))).isInstanceOf(McpEventsService.ProtocolException.class);
        owner.setIntegrationAccess(new IntegrationAccess(3, IntegrationAccess.Mode.FULL, Map.of("token-a", IntegrationAccess.Mode.FULL)));
        when(roles.privileged(owner)).thenReturn(true);
        when(roles.operator(owner)).thenReturn(true);
        assertThat(service.catalog(client).toString()).contains("operator.attention").doesNotContain("event.changed", "admin.attention");
        var roleRequest = new Request("operator.attention", Map.of("resourceId", "node"), request(null).delivery(), null, null);
        var result = service.subscribe(client, roleRequest);
        var saved = org.mockito.ArgumentCaptor.forClass(McpEventSubscription.class);
        verify(subscriptions).save(saved.capture());
        var subscription = saved.getValue();
        when(subscriptions.findById(result.id())).thenReturn(Optional.of(subscription));
        var delivery = new IntegrationWebhookDelivery();
        delivery.setSubscriptionId(subscription.id); delivery.setOwnerUserId("alice");
        delivery.setEventType("operator.attention"); delivery.setResourceExternalId("node");
        delivery.setSubscriptionGeneration(subscription.generation);
        when(roles.operator(owner)).thenReturn(false);
        assertThat(service.deliver(delivery)).isEqualTo(410);
    }

    @Test void adminModerationEventsAreAbsentAndCannotCreateACallback() {
        when(roles.operator(owner)).thenReturn(false);
        when(roles.admin(owner)).thenReturn(true);
        assertThat(service.catalog(client)).containsEntry("events", List.of());
        var omitted = new Request("admin.attention", Map.of("resourceId", "node"), request(null).delivery(), null, null);
        assertThatThrownBy(() -> service.subscribe(client, omitted)).isInstanceOf(McpEventsService.ProtocolException.class);
        verifyNoInteractions(callbacks);
        verify(subscriptions, never()).save(any());
    }
}
