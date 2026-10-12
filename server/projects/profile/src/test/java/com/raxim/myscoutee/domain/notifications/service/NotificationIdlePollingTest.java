package com.raxim.myscoutee.domain.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import com.raxim.myscoutee.domain.notifications.document.*;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;

@Tag("embedded")
class NotificationIdlePollingTest {
    @Test void idleReconciliationDoesNotRewriteStateAndOnlyDueScopedWorkEntersScheduling() {
        try (var mongod = Mongod.instance().start(Version.Main.V6_0)) {
            var address = mongod.current().getServerAddress();
            try (var client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
                var mongo = new MongoTemplate(client, "notification-idle");
                var rules = mongo.getCollection("notificationRuleConfigs");
                rules.insertOne(new Document("_id", "rule").append("ruleKey", "notification-outbox")
                        .append("baseGroupId", "work"));
                var registry = new NotificationRuleRegistryService(null, null, mongo, null);
                JobGroupContext.run("work", registry::refreshOutboxPendingCount);
                var initial = rules.find(new Document("_id", "rule")).first();
                assertThat(initial.get("runState", Document.class).getInteger("pendingCount")).isZero();
                // A fixed sentinel proves that no timestamp-only write takes place.
                rules.updateOne(new Document("_id", "rule"), new Document("$set",
                        new Document("runState.pendingCountUpdatedAtIso", "unchanged")));
                JobGroupContext.run("work", registry::refreshOutboxPendingCount);
                assertThat(rules.find(new Document("_id", "rule")).first().get("runState", Document.class)
                        .getString("pendingCountUpdatedAtIso")).isEqualTo("unchanged");

                var admission = mock(NotificationRuleRegistryService.class);
                var config = new NotificationRuleConfig(); config.setEnabled(true);
                when(admission.findRule("notification-outbox")).thenReturn(Optional.of(config));
                when(admission.shouldRunScheduledRule(eq("notification-outbox"), any())).thenReturn(true);
                ObjectProvider<NotificationRuleRegistryService> provider = mock(ObjectProvider.class);
                when(provider.getIfAvailable()).thenReturn(admission);
                var worker = new NotificationOutboxWorkerService(new ConfigProperties(), null, null, null,
                        mongo, null, null, null, null, 50, 500, 5, 300000L, 60000L, 0, 24, null);
                worker.setNotificationRuleRegistryServiceProvider(provider);
                Runnable tick = () -> JobGroupContext.run("work", () -> ReflectionTestUtils.invokeMethod(
                        worker, "processDueNotificationsForCurrentDatabaseInGroup"));
                tick.run();
                verify(admission, never()).findRule(any());
                var item = new NotificationOutboxItem(); item.setBaseGroupId("other");
                item.setStatus("pending"); mongo.save(item); tick.run();
                verify(admission, never()).findRule(any());
                item.setBaseGroupId("work"); item.setNextRetryAt(Instant.now().plusSeconds(3600).toString());
                mongo.save(item); tick.run(); verify(admission, never()).findRule(any());
                item.setNextRetryAt(Instant.now().minusSeconds(60).toString()); mongo.save(item); tick.run();
                verify(admission).beginScheduledRun(eq("notification-outbox"), any(), any(), any());
                verify(admission, never()).recordRun(any(), anyInt(), any(), any(), any(), any());

                JobGroupContext.run("work", registry::refreshOutboxPendingCount);
                assertThat(rules.find(new Document("_id", "rule")).first().get("runState", Document.class)
                        .getInteger("pendingCount")).isEqualTo(1);
                item.setStatus("sent"); mongo.save(item);
                JobGroupContext.run("work", registry::refreshOutboxPendingCount);
                assertThat(rules.find(new Document("_id", "rule")).first().get("runState", Document.class)
                        .getInteger("pendingCount")).isZero();
                clearInvocations(admission); tick.run(); verify(admission, never()).findRule(any());

                doAnswer(call -> { JobGroupContext.run("work", call.getArgument(1, Runnable.class)); return null; })
                        .when(admission).forEachGroup(any(String.class), any(Runnable.class));
                item.setStatus("pending"); item.setFirebaseDeliveryRequested(false);
                Instant lease = Instant.now().plusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
                item.setNextRetryAt(lease.minusSeconds(3600).toString()); item.setLockedUntil(lease.toString()); mongo.save(item);
                Instant wake = ReflectionTestUtils.invokeMethod(worker, "nextOutboxWakeup");
                assertThat(wake).isEqualTo(lease);
                item.setLockedUntil(null); mongo.save(item);
                wake = ReflectionTestUtils.invokeMethod(worker, "nextOutboxWakeup");
                assertThat(wake).isEqualTo(lease.minusSeconds(3600));
                config.setEnabled(false);
                assertThat((Instant) ReflectionTestUtils.invokeMethod(worker, "nextOutboxWakeup")).isNull();
                config.setEnabled(true); item.setStatus("sent"); mongo.save(item);
                assertThat((Instant) ReflectionTestUtils.invokeMethod(worker, "nextOutboxWakeup")).isNull();
            }
        }
    }
}
