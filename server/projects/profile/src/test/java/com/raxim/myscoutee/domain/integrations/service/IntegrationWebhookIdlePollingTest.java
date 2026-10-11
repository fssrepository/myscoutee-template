package com.raxim.myscoutee.domain.integrations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.integrations.document.IntegrationWebhookDelivery;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import tools.jackson.databind.ObjectMapper;

@Tag("embedded")
class IntegrationWebhookIdlePollingTest {
    @Test
    void idleDatabasesSkipJobsButNewWorkAndExpiredLeasesWakeTheExistingScheduler() {
        try (var mongod = Mongod.instance().start(Version.Main.V6_0)) {
            var address = mongod.current().getServerAddress();
            try (var client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
                var real = new MongoTemplate(client, "webhook_real");
                var demo = new MongoTemplate(client, "webhook_demo");
                var routed = mock(MongoTemplate.class);
                when(routed.exists(any(Query.class), eq(IntegrationWebhookDelivery.class)))
                        .thenAnswer(call -> (MongoSessionModeContext.currentMode() == MongoSessionMode.REAL ? real : demo)
                                .exists(call.getArgument(0), IntegrationWebhookDelivery.class));
                var jobs = mock(NotificationJobs.class);
                List<MongoSessionMode> scheduled = new ArrayList<>();
                doAnswer(call -> {
                    scheduled.add(MongoSessionModeContext.currentMode());
                    return null;
                }).when(jobs).runScheduledGroups(eq("integration-webhooks"), any(Runnable.class));
                var service = new IntegrationWebhookService(null, routed, 1, 1);
                ReflectionTestUtils.setField(service, "groupRules", jobs);
                ReflectionTestUtils.setField(service, "groupDatabases", new ScheduledMongoSessionRunner("real", "demo"));
                MongoSessionModeContext.set(MongoSessionMode.DEMO);
                try {
                    service.deliverPending();
                    verifyNoInteractions(jobs);
                    verify(routed, times(2)).exists(any(Query.class), eq(IntegrationWebhookDelivery.class));
                    verifyNoMoreInteractions(routed);
                    assertThat(MongoSessionModeContext.currentMode()).isEqualTo(MongoSessionMode.DEMO);

                    // Terminal deliveries and future retries/active leases must not create idle job history.
                    for (var mongo : List.of(real, demo)) {
                        for (String status : List.of("SENT", "FAILED")) save(mongo, status, Instant.now().minusSeconds(60));
                        for (String status : List.of("PENDING", "RETRY", "SENDING")) save(mongo, status, Instant.now().plusSeconds(3600));
                    }
                    service.deliverPending();
                    verifyNoInteractions(jobs);

                    // A later arrival must be visible on the next tick; no cached empty state.
                    // Test both databases independently, including expired SENDING leases after restart.
                    for (var mode : MongoSessionMode.values()) {
                        var mongo = mode == MongoSessionMode.REAL ? real : demo;
                        for (String status : List.of("PENDING", "RETRY", "SENDING")) {
                            var due = save(mongo, status, Instant.now().minusSeconds(60));
                            scheduled.clear();
                            service.deliverPending();
                            assertThat(scheduled).containsExactly(mode);
                            assertThat(MongoSessionModeContext.currentMode()).isEqualTo(MongoSessionMode.DEMO);
                            mongo.remove(due);
                            scheduled.clear();
                            service.deliverPending();
                            assertThat(scheduled).isEmpty();
                        }
                    }
                } finally {
                    service.shutdownDeliveryWorkers();
                    MongoSessionModeContext.clear();
                }
            }
        }
    }

    private IntegrationWebhookDelivery save(MongoTemplate mongo, String status, Instant due) {
        var delivery = new IntegrationWebhookDelivery();
        delivery.setStatus(status);
        delivery.setNextAttemptAtIso(due.toString());
        return mongo.save(delivery);
    }
}
