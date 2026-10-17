package com.raxim.myscoutee.domain.integrations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.sun.net.httpserver.HttpServer;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.accounts.api.*;
import com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents.RoleAttention;
import com.raxim.myscoutee.domain.integrations.document.McpEventSubscription;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import com.raxim.myscoutee.domain.integrations.repository.*;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.platform.api.JobAudience;
import com.raxim.myscoutee.domain.platform.api.SecretEncryption;

@Tag("embedded")
class IntegrationCallbackPersistenceTest {
    @Test
    void signedCallbackRetriesFromPersistedStateAndRechecksRevokedAuthority() throws Exception {
        var received = new CopyOnWriteArrayList<Map<String, String>>();
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
        String secret = "whsec_" + Base64.getEncoder().encodeToString(key);
        var receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/callback", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            var headers = exchange.getRequestHeaders();
            String id = headers.getFirst("webhook-id"), timestamp = headers.getFirst("webhook-timestamp");
            boolean signatureValid;
            try {
                Mac hmac = Mac.getInstance("HmacSHA256"); hmac.init(new SecretKeySpec(key, "HmacSHA256"));
                hmac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
                signatureValid = ("v1," + Base64.getEncoder().encodeToString(hmac.doFinal(body)))
                        .equals(headers.getFirst("webhook-signature"));
            } catch (java.security.GeneralSecurityException error) { throw new java.io.IOException(error); }
            received.add(Map.of("id", id, "subscription", headers.getFirst("X-MCP-Subscription-Id"),
                    "body", new String(body, StandardCharsets.UTF_8), "signatureValid", Boolean.toString(signatureValid)));
            exchange.sendResponseHeaders(!signatureValid ? 400 : received.size() == 1 ? 503 : 204, -1);
            exchange.close();
        });
        receiver.start();
        var callbacks = new McpEventCallbackClient(100, 100, 2);
        try (var http = HttpClient.newHttpClient(); var mongod = Mongod.instance().start(Version.Main.V6_0)) {
            // Test transport only: the production URI/DNS rules intentionally reject local callbacks.
            // Keep its real envelope, signature, limits and response handling; forward the resulting
            // HTTP request to this owned receiver instead of making any external connection.
            ((CloseableHttpClient) ReflectionTestUtils.getField(callbacks, "http")).close();
            var transport = mock(CloseableHttpClient.class);
            doAnswer(invocation -> {
                ClassicHttpRequest request = invocation.getArgument(0);
                HttpClientResponseHandler<?> handler = invocation.getArgument(1);
                var outgoing = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + receiver.getAddress().getPort() + "/callback"))
                        .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofByteArray(request.getEntity().getContent().readAllBytes()));
                for (String name : List.of("webhook-id", "webhook-timestamp", "webhook-signature", "X-MCP-Subscription-Id"))
                    outgoing.header(name, request.getFirstHeader(name).getValue());
                var result = http.send(outgoing.build(), HttpResponse.BodyHandlers.discarding());
                return handler.handleResponse(new BasicClassicHttpResponse(result.statusCode()));
            }).when(transport).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
            ReflectionTestUtils.setField(callbacks, "http", transport);
            var address = mongod.current().getServerAddress();
            try (var client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
                var mongo = new MongoTemplate(client, "callback_qualification");
                var repositories = new MongoRepositoryFactory(mongo);
                var subscriptions = repositories.getRepository(McpEventSubscriptionsRepository.class);
                var deliveries = repositories.getRepository(IntegrationWebhookDeliveriesRepository.class);
                var profile = mock(WorkspaceProfile.class);
                when(profile.getUserId()).thenReturn("operator");
                when(profile.getIntegrationAccess()).thenReturn(new IntegrationAccess(1, IntegrationAccess.Mode.FULL,
                        Map.of("token", IntegrationAccess.Mode.FULL)));
                var profiles = mock(ProfileDirectory.class); doReturn(Optional.of(profile)).when(profiles).findByUserId("operator");
                var roles = mock(IntegrationRoleAccessService.class); when(roles.operator(profile)).thenReturn(true);
                var oauth = mock(McpAuthorizationService.class);
                when(oauth.activeDeliveryGrant("grant", "operator", "token", "client")).thenReturn(true);
                var crypto = mock(SecretEncryption.class); when(crypto.decrypt(anyString(), any())).thenReturn(secret);
                var events = new McpEventsService(subscriptions, profiles, Optional.empty(), oauth, crypto, callbacks,
                        new com.raxim.myscoutee.common.config.JsonConfig().objectMapper(), mongo, roles);
                var subscription = new McpEventSubscription();
                subscription.id = "subscription"; subscription.ownerUserId = "operator"; subscription.tokenId = "token";
                subscription.clientId = "client"; subscription.grantId = "grant"; subscription.generation = "generation";
                subscription.name = "operator.attention"; subscription.resourceId = "node";
                subscription.callbackUrl = "https://callback.example/callback"; subscription.enabled = true;
                subscription.expiresAt = Instant.now().plusSeconds(3600); subscriptions.save(subscription);
                var attention = new RoleAttention("notification-1", "operator", "release-available", List.of("operator"));
                var first = worker(mongo, events);
                try {
                    first.enqueueRoleAttention(attention);
                    var original = deliveries.findAll().getFirst();
                    first.enqueueRoleAttention(attention);
                    assertThat(deliveries.count()).isEqualTo(1);
                    assertThat(deliveries.findById(original.getId()).orElseThrow().getPayloadJson()).isEqualTo(original.getPayloadJson());
                    first.deliverPending();
                    await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                            assertThat(deliveries.findById(original.getId()).orElseThrow().getStatus()).isEqualTo("RETRY"));
                    var retry = deliveries.findById(original.getId()).orElseThrow();
                    assertThat(retry.getAttemptCount()).isEqualTo(1); assertThat(retry.getLastError()).isEqualTo("http_503");
                    assertThat(Instant.parse(retry.getNextAttemptAtIso())).isAfter(Instant.now());
                    assertThat(received).hasSize(1);
                    first.shutdownDeliveryWorkers();
                    retry.setNextAttemptAtIso(Instant.now().minusSeconds(1).toString()); deliveries.save(retry);
                    var resumed = worker(mongo, events);
                    try {
                        resumed.recoverInterruptedDeliveries(); resumed.deliverPending();
                        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                                assertThat(deliveries.findById(original.getId()).orElseThrow().getStatus()).isEqualTo("SENT"));
                        assertThat(received).hasSize(2);
                        assertThat(received.get(0)).isEqualTo(received.get(1));
                        assertThat(received.get(1)).containsEntry("signatureValid", "true").containsEntry("subscription", "subscription")
                                .containsEntry("body", original.getPayloadJson()).containsEntry("id", original.getId());
                        resumed.enqueueRoleAttention(attention); resumed.deliverPending();
                        assertThat(deliveries.count()).isEqualTo(1); assertThat(received).hasSize(2);
                        // Authorization changes after queueing must stop the existing callback.
                        resumed.enqueueRoleAttention(new RoleAttention("notification-2", "operator", "release-available", List.of("operator")));
                        when(oauth.activeDeliveryGrant("grant", "operator", "token", "client")).thenReturn(false);
                        resumed.deliverPending();
                        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(deliveries.findAll())
                                .anySatisfy(row -> { assertThat(row.getStatus()).isEqualTo("FAILED"); assertThat(row.getLastError()).isEqualTo("http_410"); }));
                        assertThat(received).hasSize(2);
                    } finally { resumed.shutdownDeliveryWorkers(); }
                } finally { first.shutdownDeliveryWorkers(); }
            }
        } finally { callbacks.close(); receiver.stop(0); }
    }

    private IntegrationWebhookService worker(MongoTemplate mongo, McpEventsService events) {
        var repositories = new MongoRepositoryFactory(mongo);
        var worker = new IntegrationWebhookService(repositories.getRepository(IntegrationWebhookDeliveriesRepository.class), mongo, 1, 10);
        var databases = mock(ScheduledMongoSessionRunner.class);
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(databases).runAcrossConfiguredDatabases(any());
        var rules = mock(NotificationJobs.class);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; }).when(rules).runScheduledGroups(eq("integration-webhooks"), any());
        var audience = mock(JobAudience.class); when(audience.workspaceScope()).thenReturn(new JobAudience.WorkspaceScope(Set.of(), false));
        var owners = mock(AccountJobScopes.class); when(owners.matchingOwners(any(), any())).thenReturn(Set.of("operator"));
        ReflectionTestUtils.setField(worker, "groupDatabases", databases); ReflectionTestUtils.setField(worker, "groupRules", rules);
        ReflectionTestUtils.setField(worker, "jobGroupScope", audience); ReflectionTestUtils.setField(worker, "accountJobScopes", owners);
        ReflectionTestUtils.setField(worker, "mcpEvents", events); return worker;
    }
}
