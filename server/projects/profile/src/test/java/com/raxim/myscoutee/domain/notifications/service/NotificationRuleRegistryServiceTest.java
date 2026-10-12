package com.raxim.myscoutee.domain.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.raxim.myscoutee.domain.notifications.document.NotificationRuleConfig;
import com.raxim.myscoutee.domain.notifications.repository.NotificationRuleConfigsRepository;
import com.raxim.myscoutee.domain.administration.service.AdminTelemetryService;

@org.junit.jupiter.api.Tag("standard")
class NotificationRuleRegistryServiceTest {
    @Test
    void telemetryWorkerKeepsHistoryAndFailuresWithoutFeedingItselfOnSuccess() {
        var repository = mock(NotificationRuleConfigsRepository.class);
        var mongo = mock(MongoTemplate.class);
        var telemetry = mock(AdminTelemetryService.class);
        var socket = mock(AdminNotificationSocketSessionRegistry.class);
        var service = new NotificationRuleRegistryService(repository, socket, mongo, telemetry);
        var rule = new NotificationRuleConfig();
        rule.setRuleKey("admin-telemetry"); rule.setEnabled(true);
        when(repository.findByBaseGroupIdAndRuleKey(null, "admin-telemetry")).thenReturn(Optional.of(rule));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(NotificationRuleConfig.class)))
                .thenReturn(rule);
        service.beginRun("admin-telemetry", "start", "admin-telemetry");
        service.recordRun("admin-telemetry", 0, "completed", "done", "admin-telemetry");
        verifyNoInteractions(telemetry);
        verify(socket, times(2)).broadcastRuleRuntime(rule);
        var updates = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).findAndModify(any(Query.class), updates.capture(), any(FindAndModifyOptions.class), eq(NotificationRuleConfig.class));
        var terminal = updates.getAllValues().get(1).getUpdateObject();
        assertThat(terminal.get("$push", Document.class)).containsKey("runHistory");
        assertThat(terminal.get("$set", Document.class)).containsEntry("runState.lastRunStatus", "completed");
        service.recordRun("admin-telemetry", 0, "failed", "failure", "admin-telemetry");
        verify(telemetry).record(eq("jobs.run.failed"), eq("jobs"), eq("admin-telemetry"), eq("admin-telemetry"), eq(1L), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unchangedSchedulesAreReadFromStreamSnapshotButDueAdmissionRemainsAuthoritative() {
        var repository = mock(NotificationRuleConfigsRepository.class);
        var mongo = mock(MongoTemplate.class);
        var service = new NotificationRuleRegistryService(repository,
                mock(AdminNotificationSocketSessionRegistry.class), mongo, mock(AdminTelemetryService.class));
        var rule = new NotificationRuleConfig(); rule.setRuleKey("rule"); rule.setEnabled(false);
        when(repository.findAll()).thenReturn(java.util.List.of(rule));
        var changes = mock(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService.class);
        service.watchScheduling(changes, new com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner("same", "same"));
        var callback = ArgumentCaptor.forClass(java.util.function.Supplier.class);
        verify(changes).watch(eq("job-schedules"), any(), any(), callback.capture(), any(), any());
        callback.getValue().get();
        org.mockito.Mockito.clearInvocations(repository);
        try {
            for (var mode : com.raxim.myscoutee.common.config.mongo.MongoSessionMode.values()) {
                com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.set(mode);
                for (int i = 0; i < 5; i++) {
                    assertThat(service.configuredGroupIds(java.util.Set.of("rule"))).containsExactly((String) null);
                    assertThat(service.beginScheduledRun("rule", "tick", "worker", Instant.now())).isNull();
                    assertThat(service.shouldRunScheduledRule("rule", Instant.now())).isFalse();
                }
            }
            verifyNoInteractions(repository, mongo);
            var enabled = new NotificationRuleConfig(); enabled.setRuleKey("rule"); enabled.setEnabled(true);
            enabled.setTriggerKind("scheduled_process");
            var timing = new NotificationRuleConfig.Timing(); timing.setMode("interval");
            timing.setIntervalAmount(1); timing.setIntervalUnit("minutes"); timing.setTimezone("UTC"); timing.setTime("00:00"); enabled.setTiming(timing);
            when(repository.findAll()).thenReturn(java.util.List.of(enabled));
            callback.getValue().get();
            // A disable racing after the stream snapshot must still prevent the actual claim.
            when(repository.findByBaseGroupIdAndRuleKey(null, "rule")).thenReturn(Optional.of(rule));
            assertThat(service.beginScheduledRun("rule", "tick", "worker", Instant.now())).isNull();
            verify(repository).findByBaseGroupIdAndRuleKey(null, "rule");
            verifyNoInteractions(mongo);
        } finally { com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.clear(); }
    }

    @Test
    void scheduledClaimAloneRejectsMissingDisabledAndNotDueRulesWithoutWriting() {
        var repository = mock(NotificationRuleConfigsRepository.class);
        var mongo = mock(MongoTemplate.class);
        var telemetry = mock(AdminTelemetryService.class);
        var service = new NotificationRuleRegistryService(repository,
                mock(AdminNotificationSocketSessionRegistry.class), mongo, telemetry);
        var now = Instant.parse("2026-10-09T00:00:30Z");
        String key = "rule";
        when(repository.findByBaseGroupIdAndRuleKey(null, key)).thenReturn(Optional.empty());
        assertThat(service.beginScheduledRun(key, "scheduled", key, now)).isNull();
        var rule = new NotificationRuleConfig();
        rule.setRuleKey(key);
        rule.setEnabled(false);
        when(repository.findByBaseGroupIdAndRuleKey(null, key)).thenReturn(Optional.of(rule));
        assertThat(service.beginScheduledRun(key, "scheduled", key, now)).isNull();
        rule.setEnabled(true);
        rule.setTriggerKind("scheduled_process");
        var timing = new NotificationRuleConfig.Timing();
        timing.setMode("interval"); timing.setIntervalAmount(1); timing.setIntervalUnit("minutes");
        timing.setTimezone("UTC");
        rule.setTiming(timing);
        var state = new NotificationRuleConfig.RunState();
        state.setLastRunAtIso("2026-10-09T00:00:00Z"); rule.setRunState(state);
        assertThat(service.beginScheduledRun(key, "scheduled", key, now)).isNull();
        verifyNoInteractions(mongo, telemetry);
    }

    @Test
    void dueQueuedWorkHonorsSuspensionAndClaimsTheEnabledRuleAtomically() {
        var repository = mock(NotificationRuleConfigsRepository.class);
        var mongo = mock(MongoTemplate.class);
        var service = new NotificationRuleRegistryService(repository,
                mock(AdminNotificationSocketSessionRegistry.class), mongo, mock(AdminTelemetryService.class));
        var rule = new NotificationRuleConfig();
        rule.setRuleKey("account-deletion-warning");
        rule.setEnabled(false);
        when(repository.findByBaseGroupIdAndRuleKey(null, rule.getRuleKey())).thenReturn(Optional.of(rule));
        assertThat(service.beginQueuedRun(rule.getRuleKey(), "retry", rule.getRuleKey(), Instant.now())).isNull();
        verifyNoInteractions(mongo);
        rule.setEnabled(true);
        service.beginQueuedRun(rule.getRuleKey(), "retry", rule.getRuleKey(), Instant.now());
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class), eq(NotificationRuleConfig.class));
        assertThat(query.getValue().getQueryObject().toJson()).contains("\"enabled\": true", "runState.currentStatus", "runState.lastRunAtIso");
    }

    @Test
    void nonManualWorkersAreRecordedAsScheduledRegardlessOfTheirRunnerId() {
        assertThat(runHistoryTrigger(
                NotificationRuleRegistryService.RULE_ACCOUNT_PURGE,
                false,
                "affinity-recompute"))
                .isEqualTo("scheduled");
    }

    @Test
    void randomGraphDistinguishesScheduledAndAdminRuns() {
        assertThat(runHistoryTrigger(
                NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX,
                true,
                "scheduler"))
                .isEqualTo("scheduled");
        assertThat(runHistoryTrigger(
                NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX,
                true,
                "admin-user"))
                .isEqualTo("manual");
    }

    @Test
    void scheduledRunSkipsAWindowClaimedByManualRunAndWaitsForTheNextInterval() {
        NotificationRuleConfigsRepository repository =
                mock(NotificationRuleConfigsRepository.class);
        AdminNotificationSocketSessionRegistry socketRegistry =
                mock(AdminNotificationSocketSessionRegistry.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AdminTelemetryService adminTelemetryService = mock(AdminTelemetryService.class);
        NotificationRuleConfig rule = new NotificationRuleConfig();
        rule.setRuleKey(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX);
        rule.setEnabled(true);
        rule.setTriggerKind("scheduled_process");
        NotificationRuleConfig.Timing timing = new NotificationRuleConfig.Timing();
        timing.setMode("interval");
        timing.setIntervalAmount(1);
        timing.setIntervalUnit("minutes");
        timing.setTime("00:00");
        timing.setTimezone("UTC");
        rule.setTiming(timing);
        NotificationRuleConfig.RunState runState = new NotificationRuleConfig.RunState();
        runState.setCurrentStatus("running");
        runState.setStartedAtIso("2026-07-31T10:01:00Z");
        runState.setLastRunAtIso("2026-07-31T10:00:00Z");
        rule.setRunState(runState);
        when(repository.findByBaseGroupIdAndRuleKey(null, rule.getRuleKey())).thenReturn(Optional.of(rule));
        NotificationRuleRegistryService service =
                new NotificationRuleRegistryService(
                        repository,
                        socketRegistry,
                        mongoTemplate,
                        adminTelemetryService);

        Instant scheduledWindow = Instant.parse("2026-07-31T10:02:00Z");
        assertThat(service.shouldRunScheduledRule(rule.getRuleKey(), scheduledWindow)).isTrue();
        assertThat(service.beginScheduledRun(
                rule.getRuleKey(),
                "scheduled",
                "scheduler",
                scheduledWindow))
                .isNull();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NotificationRuleConfig.class));
        assertThat(queryCaptor.getValue().getQueryObject().toJson())
                .contains("\"runState.currentStatus\"")
                .contains("\"$ne\"")
                .contains("\"running\"");
        verifyNoInteractions(adminTelemetryService);

        runState.setCurrentStatus("completed");
        runState.setLastRunAtIso("2026-07-31T10:02:30Z");
        assertThat(service.shouldRunScheduledRule(
                rule.getRuleKey(),
                Instant.parse("2026-07-31T10:02:59Z")))
                .isFalse();
        assertThat(service.shouldRunScheduledRule(
                rule.getRuleKey(),
                Instant.parse("2026-07-31T10:03:00Z")))
                .isTrue();
    }

    @Test
    void nextScheduledRunUsesPersistedIntervalAndLastRun() {
        NotificationRuleConfig rule = new NotificationRuleConfig();
        rule.setEnabled(true);
        NotificationRuleConfig.Timing timing = new NotificationRuleConfig.Timing();
        timing.setMode("interval");
        timing.setIntervalAmount(1);
        timing.setIntervalUnit("minutes");
        timing.setTimezone("UTC");
        rule.setTiming(timing);
        NotificationRuleConfig.RunState runState = new NotificationRuleConfig.RunState();
        runState.setLastRunAtIso("2026-09-02T06:49:51.458Z");
        rule.setRunState(runState);
        NotificationRuleRegistryService service = new NotificationRuleRegistryService(
                mock(NotificationRuleConfigsRepository.class),
                mock(AdminNotificationSocketSessionRegistry.class),
                mock(MongoTemplate.class),
                mock(AdminTelemetryService.class));

        assertThat(service.nextScheduledRunAtIso(rule, Instant.parse("2026-09-02T06:50:00Z")))
                .isEqualTo("2026-09-02T06:50:00Z");
    }

    @Test
    void disabledRuleHasNoNextScheduledRun() {
        NotificationRuleConfig rule = new NotificationRuleConfig();
        rule.setEnabled(false);
        NotificationRuleRegistryService service = new NotificationRuleRegistryService(
                mock(NotificationRuleConfigsRepository.class),
                mock(AdminNotificationSocketSessionRegistry.class),
                mock(MongoTemplate.class),
                mock(AdminTelemetryService.class));

        assertThat(service.nextScheduledRunAtIso(rule, Instant.parse("2026-09-02T06:50:00Z")))
                .isEmpty();
    }

    @Test
    void successfulRunEmitsOneStartAndOneTerminalEventRegardlessOfProcessedCount() {
        NotificationRuleConfigsRepository repository = mock(NotificationRuleConfigsRepository.class);
        AdminNotificationSocketSessionRegistry socketRegistry = mock(AdminNotificationSocketSessionRegistry.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AdminTelemetryService adminTelemetryService = mock(AdminTelemetryService.class);
        NotificationRuleConfig rule = new NotificationRuleConfig();
        rule.setRuleKey(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX);
        rule.setManualRunEnabled(true);
        when(repository.findByBaseGroupIdAndRuleKey(null, rule.getRuleKey())).thenReturn(Optional.of(rule));
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NotificationRuleConfig.class)))
                .thenReturn(rule);
        NotificationRuleRegistryService service = new NotificationRuleRegistryService(
                repository,
                socketRegistry,
                mongoTemplate,
                adminTelemetryService);

        Instant startedAt = Instant.parse("2026-07-29T10:00:00Z");
        service.beginRun(rule.getRuleKey(), "manual start", "admin-user", startedAt);
        service.recordRun(rule.getRuleKey(), 200, "completed", "done", "admin-user", startedAt);

        verify(adminTelemetryService).record(
                eq("jobs.run.started"),
                eq("jobs"),
                eq(rule.getRuleKey()),
                eq("admin-user"),
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(tags -> "manual".equals(tags.get("trigger"))));
        verify(adminTelemetryService).record(
                eq("jobs.run.completed"),
                eq("jobs"),
                eq(rule.getRuleKey()),
                eq("admin-user"),
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(tags -> "completed".equals(tags.get("status"))
                        && "manual".equals(tags.get("trigger"))
                        && "200".equals(tags.get("processedCount"))
                        && tags.containsKey("durationMillis")));
        verify(adminTelemetryService, times(2)).record(any(), any(), any(), any(), eq(1L), any());
    }

    @Test
    void failedRunEmitsTerminalEventOnlyWhenAtomicUpdateSucceeds() {
        NotificationRuleConfigsRepository repository = mock(NotificationRuleConfigsRepository.class);
        AdminNotificationSocketSessionRegistry socketRegistry = mock(AdminNotificationSocketSessionRegistry.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AdminTelemetryService adminTelemetryService = mock(AdminTelemetryService.class);
        NotificationRuleConfig rule = new NotificationRuleConfig();
        rule.setRuleKey(NotificationRuleRegistryService.RULE_ACCOUNT_PURGE);
        rule.setManualRunEnabled(false);
        when(repository.findByBaseGroupIdAndRuleKey(null, rule.getRuleKey())).thenReturn(Optional.of(rule));
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NotificationRuleConfig.class)))
                .thenReturn(rule, (NotificationRuleConfig) null);
        NotificationRuleRegistryService service = new NotificationRuleRegistryService(
                repository,
                socketRegistry,
                mongoTemplate,
                adminTelemetryService);

        service.recordRun(rule.getRuleKey(), 0, "failed", "boom", "affinity-worker");
        service.recordRun(rule.getRuleKey(), 0, "completed", "stale completion", "affinity-worker");

        verify(adminTelemetryService).record(
                eq("jobs.run.failed"),
                eq("jobs"),
                eq(rule.getRuleKey()),
                eq("affinity-worker"),
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(tags -> "scheduled".equals(tags.get("trigger"))));
        verify(adminTelemetryService, times(1)).record(any(), any(), any(), any(), eq(1L), any());
    }

    private String runHistoryTrigger(
            String ruleKey,
            boolean manualRunEnabled,
            String actorUserId) {
        NotificationRuleConfigsRepository repository =
                mock(NotificationRuleConfigsRepository.class);
        AdminNotificationSocketSessionRegistry socketRegistry =
                mock(AdminNotificationSocketSessionRegistry.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AdminTelemetryService adminTelemetryService = mock(AdminTelemetryService.class);
        NotificationRuleConfig rule = new NotificationRuleConfig();
        rule.setRuleKey(ruleKey);
        rule.setManualRunEnabled(manualRunEnabled);
        when(repository.findByBaseGroupIdAndRuleKey(null, ruleKey)).thenReturn(Optional.of(rule));
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NotificationRuleConfig.class)))
                .thenReturn(rule);
        NotificationRuleRegistryService service =
                new NotificationRuleRegistryService(
                        repository,
                        socketRegistry,
                        mongoTemplate,
                        adminTelemetryService);

        service.recordRun(
                ruleKey,
                1,
                "completed",
                "test",
                actorUserId,
                Instant.parse("2026-07-29T10:00:00Z"));

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                any(Query.class),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NotificationRuleConfig.class));
        Document push = updateCaptor.getValue().getUpdateObject().get(
                "$push",
                Document.class);
        Update.Modifiers history = (Update.Modifiers) push.get("runHistory");
        Object[] entries = (Object[]) history.getModifiers().stream()
                .filter(modifier -> "$each".equals(modifier.getKey()))
                .findFirst()
                .orElseThrow()
                .getValue();
        Object entry = entries[0];
        if (entry instanceof Document document) {
            return document.getString("trigger");
        }
        return ((NotificationRuleConfig.RunHistoryEntry) entry).getTrigger();
    }
}
