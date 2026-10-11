package com.raxim.myscoutee.domain.notifications.service;

import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.notifications.document.NotificationRuleConfig;
import com.raxim.myscoutee.domain.notifications.repository.NotificationRuleConfigsRepository;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;

@Service
public class NotificationRuleRegistryService implements com.raxim.myscoutee.domain.notifications.api.NotificationJobs {

    private static final String SYSTEM_USER = "notification-registry";
    private static final String INTERVAL_UNIT_SECONDS = "seconds";
    private static final String INTERVAL_UNIT_MINUTES = "minutes";
    private static final String INTERVAL_UNIT_HOURS = "hours";
    private static final String INTERVAL_UNIT_DAYS = "days";
    private static final String INTERVAL_UNIT_WEEKS = "weeks";
    private static final String INTERVAL_UNIT_MONTHS = "months";
    private static final String INTERVAL_UNIT_YEARS = "years";
    private static final int RUN_HISTORY_LIMIT = 12;
    private final NotificationRuleConfigsRepository notificationRuleConfigsRepository;
    private final AdminNotificationSocketSessionRegistry socketSessionRegistry;
    private final MongoTemplate mongoTemplate;
    private final OperationalTelemetry adminTelemetryService;

    private final Map<com.raxim.myscoutee.common.config.mongo.MongoSessionMode, SchedulingSnapshot> schedulingSnapshots = new java.util.concurrent.ConcurrentHashMap<>();
    private record SchedulingSnapshot(long revision, List<NotificationRuleConfig> rules) {}
    private final java.util.concurrent.atomic.AtomicLong schedulingRevision = new java.util.concurrent.atomic.AtomicLong();
    private boolean streamScheduling;
    private boolean distinctSchedulingDatabases;

    @org.springframework.beans.factory.annotation.Autowired
    void watchScheduling(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes,
            com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner databases) {
        streamScheduling = true;
        distinctSchedulingDatabases = databases.hasDistinctDemoDatabase();
        changes.watch("job-schedules", Set.of("notificationRuleConfigs"),
                Set.of("baseGroupId", "ruleKey", "enabled", "triggerKind", "timing", "scheduleSlots", "runState.lastRunAtIso"), () -> {
                    refreshSchedulingSnapshot();
                    return null;
                }, this::invalidateSchedulingSnapshot, this::invalidateSchedulingSnapshot);
    }

    private void invalidateSchedulingSnapshot() {
        // A generation fence also rejects a load that started before this invalidation.
        schedulingRevision.incrementAndGet();
    }

    private synchronized void refreshSchedulingSnapshot() {
        long revision = schedulingRevision.get();
        var snapshot = new SchedulingSnapshot(revision, List.copyOf(notificationRuleConfigsRepository.findAll()));
        var mode = com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode();
        schedulingSnapshots.put(mode, snapshot);
        if (!distinctSchedulingDatabases) {
            for (var alias : com.raxim.myscoutee.common.config.mongo.MongoSessionMode.values()) schedulingSnapshots.put(alias, snapshot);
        }
    }

    private synchronized List<NotificationRuleConfig> schedulingSnapshot() {
        var mode = com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode();
        while (true) {
            var snapshot = schedulingSnapshots.get(mode);
            if (snapshot != null && snapshot.revision() == schedulingRevision.get()) return snapshot.rules();
            refreshSchedulingSnapshot();
        }
    }

    private NotificationRuleConfig scheduledCandidate(String key) {
        return schedulingSnapshot().stream().filter(rule -> java.util.Objects.equals(rule.getBaseGroupId(), JobGroupContext.groupId())
                && safeText(key).equals(rule.getRuleKey())).findFirst().orElse(null);
    }

    private record IntervalSpec(int amount, String unit, int seconds, int minutes) {}

    public NotificationRuleRegistryService(
            NotificationRuleConfigsRepository notificationRuleConfigsRepository,
            AdminNotificationSocketSessionRegistry socketSessionRegistry,
            MongoTemplate mongoTemplate,
            OperationalTelemetry adminTelemetryService) {
        this.notificationRuleConfigsRepository = notificationRuleConfigsRepository;
        this.socketSessionRegistry = socketSessionRegistry;
        this.mongoTemplate = mongoTemplate;
        this.adminTelemetryService = adminTelemetryService;
    }

    @Override public List<String> configuredGroupIds(Set<String> ruleKeys) {
        var groups = new java.util.LinkedHashSet<String>();
        if (streamScheduling) {
            for (var rule : schedulingSnapshot()) if (ruleKeys.contains(rule.getRuleKey())) groups.add(rule.getBaseGroupId());
        } else {
            for (var rule : notificationRuleConfigsRepository.findByRuleKeyIn(ruleKeys)) groups.add(rule.getBaseGroupId());
        }
        return new ArrayList<>(groups);
    }

    private Criteria ruleCriteria(String ruleKey) {
        return Criteria.where("baseGroupId").is(JobGroupContext.groupId()).and("ruleKey").is(ruleKey);
    }

    public List<NotificationRuleConfig> rules() {
        return sorted(notificationRuleConfigsRepository.findByBaseGroupId(JobGroupContext.groupId()));
    }

    public Optional<NotificationRuleConfig> findRule(String ruleKey) {
        return notificationRuleConfigsRepository.findByBaseGroupIdAndRuleKey(JobGroupContext.groupId(), safeText(ruleKey));
    }

    public boolean shouldRunScheduledRule(String ruleKey, Instant now) {
        if (streamScheduling && !isScheduledRuleDue(scheduledCandidate(ruleKey), now)) return false;
        NotificationRuleConfig rule = findRule(ruleKey).orElse(null);
        return isScheduledRuleDue(rule, now);
    }

    public String nextScheduledRunAtIso(NotificationRuleConfig rule, Instant now) {
        if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) {
            return "";
        }
        NotificationRuleConfig.Timing timing = rule.getTiming();
        if (timing == null || !normalizeScheduleSlots(rule.getScheduleSlots(), timing).isEmpty()) {
            return "";
        }
        Instant resolvedNow = now == null ? Instant.now() : now;
        String mode = safeText(timing.getMode()).toLowerCase();
        if ("interval".equals(mode)) {
            Instant lastRunAt = parseInstant(rule.getRunState() == null ? null : rule.getRunState().getLastRunAtIso());
            if (lastRunAt == null) {
                return resolvedNow.toString();
            }
            ZoneId zoneId = ZoneId.of(firstNonBlank(timing.getTimezone(), "UTC"));
            IntervalSpec interval = intervalSpec(timing);
            ZonedDateTime base = lastRunAt.atZone(zoneId);
            if (INTERVAL_UNIT_MINUTES.equals(normalizeIntervalUnit(interval.unit()))) {
                base = base.truncatedTo(ChronoUnit.MINUTES);
            }
            return plusInterval(base, interval).toInstant().toString();
        }
        if ("yearly".equals(mode)) {
            Instant occurrence = yearlyOccurrence(timing, resolvedNow);
            return occurrence == null ? "" : occurrence.toString();
        }
        return "";
    }

    private boolean isScheduledRuleDue(NotificationRuleConfig rule, Instant now) {
        if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) {
            return false;
        }
        String triggerKind = safeText(rule.getTriggerKind());
        if (!"scheduled_process".equals(triggerKind) && !"timed".equals(triggerKind)) {
            return false;
        }
        NotificationRuleConfig.Timing timing = rule.getTiming() == null ? new NotificationRuleConfig.Timing() : rule.getTiming();
        Instant resolvedNow = now == null ? Instant.now() : now;
        List<NotificationRuleConfig.ScheduleSlot> slots = normalizeScheduleSlots(rule.getScheduleSlots(), timing);
        if (!slots.isEmpty()) {
            Instant lastRunAt = parseInstant(rule.getRunState() == null ? null : rule.getRunState().getLastRunAtIso());
            for (NotificationRuleConfig.ScheduleSlot slot : slots) {
                Instant occurrence = slotOccurrence(slot, resolvedNow);
                if (occurrence != null && !resolvedNow.isBefore(occurrence) && (lastRunAt == null || lastRunAt.isBefore(occurrence))) {
                    return true;
                }
            }
            return false;
        }

        String mode = safeText(timing.getMode()).toLowerCase();
        if ("interval".equals(mode)) {
            Instant lastRunAt = parseInstant(rule.getRunState() == null ? null : rule.getRunState().getLastRunAtIso());
            return isIntervalDue(timing, lastRunAt, resolvedNow);
        }
        if ("yearly".equals(mode)) {
            Instant occurrence = yearlyOccurrence(timing, resolvedNow);
            if (occurrence == null || resolvedNow.isBefore(occurrence)) {
                return false;
            }
            Instant lastRunAt = parseInstant(rule.getRunState() == null ? null : rule.getRunState().getLastRunAtIso());
            return lastRunAt == null || lastRunAt.isBefore(occurrence);
        }
        return false;
    }

    public synchronized NotificationRuleConfig beginRun(String ruleKey, String detail, String actorUserId) {
        return beginRun(ruleKey, detail, actorUserId, Instant.now());
    }

    public synchronized NotificationRuleConfig beginRun(String ruleKey, String detail, String actorUserId, Instant startedAt) {
        return beginRunAtomic(ruleKey, detail, actorUserId, startedAt, null);
    }

    public synchronized NotificationRuleConfig beginScheduledRun(
            String ruleKey,
            String detail,
            String actorUserId,
            Instant startedAt) {
        Instant effectiveStartedAt = startedAt == null ? Instant.now() : startedAt;
        // Reject unchanged not-due schedules in memory. Positive admission still reads and claims current owner state.
        if (streamScheduling && !isScheduledRuleDue(scheduledCandidate(ruleKey), effectiveStartedAt)) return null;
        NotificationRuleConfig candidate = findRule(ruleKey).orElse(null);
        if (!isScheduledRuleDue(candidate, effectiveStartedAt)) {
            return null;
        }
        String expectedLastRunAtIso = candidate.getRunState() == null
                ? ""
                : safeText(candidate.getRunState().getLastRunAtIso());
        return beginRunAtomic(ruleKey, detail, actorUserId, effectiveStartedAt, expectedLastRunAtIso);
    }

    /** Acquire due durable work without delaying it until the rule's next periodic scan. */
    public synchronized NotificationRuleConfig beginQueuedRun(
            String ruleKey, String detail, String actorUserId, Instant startedAt) {
        NotificationRuleConfig candidate = findRule(ruleKey).orElse(null);
        if (candidate == null || !Boolean.TRUE.equals(candidate.getEnabled())) return null;
        String expectedLastRunAtIso = candidate.getRunState() == null
                ? "" : safeText(candidate.getRunState().getLastRunAtIso());
        return beginRunAtomic(ruleKey, detail, actorUserId, startedAt, expectedLastRunAtIso);
    }

    private NotificationRuleConfig beginRunAtomic(
            String ruleKey,
            String detail,
            String actorUserId,
            Instant startedAt,
            String expectedLastRunAtIso) {
        String nowIso = Instant.now().toString();
        String startedAtIso = (startedAt == null ? Instant.now() : startedAt).toString();
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(ruleCriteria(safeText(ruleKey)));
        criteria.add(new Criteria().orOperator(
                Criteria.where("runState.currentStatus").ne("running"),
                Criteria.where("runState.startedAtIso").exists(false),
                Criteria.where("runState.startedAtIso").is(null),
                Criteria.where("runState.startedAtIso").lte(Instant.now().minus(2, ChronoUnit.HOURS).toString())));
        if (expectedLastRunAtIso != null) {
            criteria.add(StringUtils.hasText(expectedLastRunAtIso)
                    ? Criteria.where("runState.lastRunAtIso").is(expectedLastRunAtIso)
                    : new Criteria().orOperator(
                            Criteria.where("runState.lastRunAtIso").exists(false),
                            Criteria.where("runState.lastRunAtIso").is(null),
                            Criteria.where("runState.lastRunAtIso").is("")));
            criteria.add(Criteria.where("enabled").is(true));
        }
        Update update = new Update()
                .set("runState.currentStatus", "running")
                .set("runState.progressPercent", 0)
                .set("runState.progressDetail", firstNonBlank(detail, "Starting scheduled process."))
                .set("runState.startedAtIso", startedAtIso)
                .set("runState.finishedAtIso", "")
                .set("runState.durationMillis", 0L)
                .set("updatedDate", nowIso)
                .set("updatedUser", firstNonBlank(actorUserId, SYSTEM_USER));
        NotificationRuleConfig saved = mongoTemplate.findAndModify(
                Query.query(new Criteria().andOperator(criteria)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                NotificationRuleConfig.class);
        if (saved != null) {
            // The telemetry consumer must not continuously feed its own input stream.
            if (!"admin-telemetry".equals(ruleKey)) adminTelemetryService.record(
                    "jobs.run.started",
                    "jobs",
                    safeText(ruleKey),
                    firstNonBlank(actorUserId, SYSTEM_USER),
                    1L,
                    Map.of("trigger", runTrigger(saved, actorUserId)));
            socketSessionRegistry.broadcastRuleRuntime(saved);
        }
        return saved;
    }

    public synchronized NotificationRuleConfig recordRun(String ruleKey, int count, String status, String detail, String actorUserId) {
        return recordRun(ruleKey, count, status, detail, actorUserId, null);
    }

    public synchronized NotificationRuleConfig recordRun(
            String ruleKey,
            int count,
            String status,
            String detail,
            String actorUserId,
            Instant startedAtOverride) {
        NotificationRuleConfig rule = findRule(ruleKey).orElse(null);
        if (rule == null) {
            return null;
        }
        String finishedAtIso = Instant.now().toString();
        NotificationRuleConfig.RunState runState = rule.getRunState() == null
                ? new NotificationRuleConfig.RunState()
                : rule.getRunState();
        String startedAtIso = startedAtOverride == null
                ? firstNonBlank(runState.getStartedAtIso(), finishedAtIso)
                : startedAtOverride.toString();
        Instant startedAt = parseInstant(startedAtIso);
        Instant finishedAt = parseInstant(finishedAtIso);
        long durationMillis = startedAt == null || finishedAt == null
                ? 0L
                : Math.max(0L, finishedAt.toEpochMilli() - startedAt.toEpochMilli());
        Update update = new Update()
                .set("runState.currentStatus", firstNonBlank(status, "completed"))
                .set("runState.progressPercent", 100)
                .set("runState.progressDetail", safeText(detail))
                .set("runState.finishedAtIso", finishedAtIso)
                .set("runState.durationMillis", durationMillis)
                .set("runState.lastRunAtIso", finishedAtIso)
                .set("runState.lastRunCount", Math.max(0, count))
                .set("runState.lastRunStatus", firstNonBlank(status, "completed"))
                .set("runState.lastRunDetail", safeText(detail))
                .set("runState.lastRunUser", firstNonBlank(actorUserId, SYSTEM_USER))
                .set("updatedDate", finishedAtIso)
                .set("updatedUser", firstNonBlank(actorUserId, SYSTEM_USER));
        if ("completed".equals(firstNonBlank(status, "completed"))) update.set("runState.pendingClaimedCount", 0);
        update.push("runHistory")
                .atPosition(0)
                .slice(RUN_HISTORY_LIMIT)
                .each(runHistoryEntry(
                        Boolean.TRUE.equals(rule.getManualRunEnabled()),
                        startedAtIso,
                        finishedAtIso,
                        durationMillis,
                        count,
                        status,
                        detail,
                        actorUserId));
        Criteria criteria = ruleCriteria(safeText(ruleKey));
        if (startedAtOverride != null) {
            criteria = new Criteria().andOperator(
                    criteria,
                    Criteria.where("runState.startedAtIso").is(startedAtOverride.toString()),
                    Criteria.where("runState.currentStatus").is("running"));
        }
        NotificationRuleConfig saved = mongoTemplate.findAndModify(
                Query.query(criteria),
                update,
                FindAndModifyOptions.options().returnNew(true),
                NotificationRuleConfig.class);
        if (saved == null && startedAtOverride != null) {
            var current = findRule(ruleKey).orElse(null);
            var state = current == null ? null : current.getRunState();
            if (state != null && startedAtIso.equals(state.getStartedAtIso())
                    && firstNonBlank(status, "completed").equals(state.getCurrentStatus())) {
                return current; // Lost acknowledgment: the terminal receipt already exists.
            }
        }
        if (saved != null) {
            String normalizedStatus = normalizeTerminalStatus(status);
            // Preserve failures and job history; omit only routine self-observation.
            if (!"admin-telemetry".equals(ruleKey) || !"completed".equals(normalizedStatus)) adminTelemetryService.record(
                    "jobs.run." + normalizedStatus,
                    "jobs",
                    safeText(ruleKey),
                    firstNonBlank(actorUserId, SYSTEM_USER),
                    1L,
                    Map.of(
                            "status", normalizedStatus,
                            "trigger", runTrigger(saved, actorUserId),
                            "processedCount", String.valueOf(Math.max(0, count)),
                            "durationMillis", String.valueOf(durationMillis)));
            socketSessionRegistry.broadcastRuleRuntime(saved);
        }
        return saved;
    }

    public synchronized NotificationRuleConfig updateRunProgress(String ruleKey, int progressPercent, String detail, String actorUserId) {
        NotificationRuleConfig rule = findRule(ruleKey).orElse(null);
        if (rule == null) {
            return null;
        }
        String nowIso = Instant.now().toString();
        String startedAtIso = rule.getRunState() == null
                ? nowIso
                : firstNonBlank(rule.getRunState().getStartedAtIso(), nowIso);
        NotificationRuleConfig saved = mongoTemplate.findAndModify(
                Query.query(ruleCriteria(safeText(ruleKey))),
                new Update()
                        .set("runState.currentStatus", "running")
                        .set("runState.progressPercent", clamp(progressPercent, 0, 99))
                        .set("runState.progressDetail", firstNonBlank(detail, "Scheduled process is running."))
                        .set("runState.startedAtIso", startedAtIso)
                        .set("runState.finishedAtIso", "")
                        .set("runState.durationMillis", 0L)
                        .set("updatedDate", nowIso)
                        .set("updatedUser", firstNonBlank(actorUserId, SYSTEM_USER)),
                FindAndModifyOptions.options().returnNew(true),
                NotificationRuleConfig.class);
        if (saved != null) {
            socketSessionRegistry.broadcastRuleRuntime(saved);
        }
        return saved;
    }

    /** Write-side projection of this owner's durable queue, including expired leases.
     * An interrupted insert/terminal update cannot permanently lose a delta.
     * The existing outbox worker also refreshes this metric before its due gate.
     */
    public void refreshOutboxPendingCount() {
        long pending = mongoTemplate.count(Query.query(Criteria.where("baseGroupId").is(JobGroupContext.groupId())
                .and("status").in(
                        com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem.STATUS_PENDING,
                        com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem.STATUS_RUNNING)),
                com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem.class);
        int count = (int) Math.min(Integer.MAX_VALUE, pending);
        String unit = "admin.jobs.rule.notification.outbox";
        // Reconciliation still runs, but unchanged queues must not create writes/replication.
        mongoTemplate.updateFirst(Query.query(new Criteria().andOperator(
                ruleCriteria(RULE_NOTIFICATION_OUTBOX), new Criteria().orOperator(
                        Criteria.where("runState.pendingCount").ne(count),
                        Criteria.where("runState.pendingCountUnit").ne(unit)))), new Update()
                .set("runState.pendingCount", count)
                .set("runState.pendingCountUnit", unit)
                .set("runState.pendingCountUpdatedAtIso", Instant.now().toString()), NotificationRuleConfig.class);
    }

    public synchronized NotificationRuleConfig adjustPendingCount(String ruleKey, int delta, String unit) {
        String normalizedRuleKey = safeText(ruleKey);
        if (!StringUtils.hasText(normalizedRuleKey) || delta == 0) {
            return findRule(normalizedRuleKey).orElse(null);
        }
        String nowIso = Instant.now().toString();
        AggregationExpression pendingCount = context -> new Document(
                "$max",
                List.of(
                        0,
                        new Document(
                                "$add",
                                List.of(
                                        new Document(
                                                "$ifNull",
                                                List.of("$runState.pendingCount", 0)),
                                        delta))));
        AggregationUpdate update = AggregationUpdate.update()
                .set("runState").toValue((AggregationExpression) context -> new Document(
                            "$ifNull",
                            List.of("$runState", new Document())))
                .set("runState.pendingCount").toValue(pendingCount)
                .set("runState.pendingCountUnit").toValue(firstNonBlank(unit, "records"))
                .set("runState.pendingCountUpdatedAtIso").toValue(nowIso);
        NotificationRuleConfig saved = mongoTemplate.findAndModify(
                Query.query(ruleCriteria(normalizedRuleKey)),
                update,
                FindAndModifyOptions.options().returnNew(true),
                NotificationRuleConfig.class);
        return saved;
    }

    public synchronized int claimPendingCount(String ruleKey) {
        return claimPendingCount(ruleKey, currentRunStart(ruleKey));
    }

    public synchronized int claimPendingCount(String ruleKey, Instant startedAt) {
        if (startedAt == null) return 0;
        String stamp = startedAt.toString();
        var sameClaim = new Document("$eq", List.of("$runState.pendingClaimStartedAtIso", stamp));
        var oldClaim = new Document("$ifNull", List.of("$runState.pendingClaimedCount", 0));
        var pending = new Document("$ifNull", List.of("$runState.pendingCount", 0));
        // An expired worker's unacknowledged claim travels into the next run.
        // A completed run clears its claim in the same update as its receipt.
        var update = AggregationUpdate.update()
                .set("runState.pendingClaimedCount").toValue((AggregationExpression) context ->
                        new Document("$cond", List.of(sameClaim, oldClaim, new Document("$add", List.of(pending, oldClaim)))))
                .set("runState.pendingCount").toValue((AggregationExpression) context ->
                        new Document("$cond", List.of(sameClaim, pending, 0)))
                .set("runState.pendingClaimStartedAtIso").toValue(stamp)
                .set("runState.pendingCountUpdatedAtIso").toValue(Instant.now().toString());
        var saved = mongoTemplate.findAndModify(Query.query(new Criteria().andOperator(ruleCriteria(safeText(ruleKey)),
                        Criteria.where("runState.startedAtIso").is(stamp), Criteria.where("runState.currentStatus").is("running"))),
                update, FindAndModifyOptions.options().returnNew(true), NotificationRuleConfig.class);
        if (saved == null || saved.getRunState() == null) return 0;
        socketSessionRegistry.broadcastRuleRuntime(saved);
        Integer count = saved.getRunState().getPendingClaimedCount();
        return Math.max(0, count == null ? 0 : count);
    }

    public synchronized void restoreClaimedPendingCount(String ruleKey, int claimedCount, String unit) {
        restoreClaimedPendingCount(ruleKey, claimedCount, unit, currentRunStart(ruleKey));
    }

    public synchronized void restoreClaimedPendingCount(String ruleKey, int claimedCount, String unit, Instant startedAt) {
        if (startedAt == null) return;
        // Use the durable receipt, including when the claim reply was lost and
        // the caller's local count is still zero. Repeating restore adds zero.
        var update = AggregationUpdate.update()
                .set("runState.pendingCount").toValue((AggregationExpression) context -> new Document("$add", List.of(
                        new Document("$ifNull", List.of("$runState.pendingCount", 0)),
                        new Document("$ifNull", List.of("$runState.pendingClaimedCount", 0)))))
                .set("runState.pendingClaimedCount").toValue(0)
                .set("runState.pendingCountUnit").toValue(firstNonBlank(unit, "records"))
                .set("runState.pendingCountUpdatedAtIso").toValue(Instant.now().toString());
        mongoTemplate.updateFirst(Query.query(new Criteria().andOperator(ruleCriteria(safeText(ruleKey)),
                        Criteria.where("runState.startedAtIso").is(startedAt.toString()),
                        Criteria.where("runState.pendingClaimStartedAtIso").is(startedAt.toString()),
                        Criteria.where("runState.currentStatus").is("running"))), update, NotificationRuleConfig.class);
    }

    private Instant currentRunStart(String ruleKey) {
        var rule = findRule(ruleKey).orElse(null);
        return rule == null || rule.getRunState() == null ? null : parseInstant(rule.getRunState().getStartedAtIso());
    }

    public void broadcastRunProgress(
            String ruleKey,
            NotificationRuleConfig.RunState runState,
            List<NotificationRuleConfig.RunHistoryEntry> runHistory,
            String actorUserId) {
        socketSessionRegistry.broadcastRuleRuntime(
                ruleKey,
                runState,
                runHistory,
                Instant.now().toString(),
                firstNonBlank(actorUserId, SYSTEM_USER));
    }

    public synchronized List<NotificationRuleConfig> saveRules(List<NotificationRuleConfig> incomingRules, String actorUserId) {
        try {
            return saveRulesInCurrentGroup(incomingRules, actorUserId);
        } finally {
            // The admin response must not precede this owner's updated scheduling view,
            // including a partially committed batch. Other instances receive the stream event.
            if (streamScheduling) refreshSchedulingSnapshot();
        }
    }

    private List<NotificationRuleConfig> saveRulesInCurrentGroup(List<NotificationRuleConfig> incomingRules, String actorUserId) {
        if (incomingRules == null || incomingRules.isEmpty()) {
            return rules();
        }
        Map<String, NotificationRuleConfig> existingByKey = new LinkedHashMap<>();
        for (NotificationRuleConfig existing : notificationRuleConfigsRepository.findByBaseGroupId(JobGroupContext.groupId())) {
            if (existing != null && StringUtils.hasText(safeText(existing.getRuleKey()))) {
                existingByKey.put(safeText(existing.getRuleKey()), existing);
            }
        }
        String nowIso = Instant.now().toString();
        for (NotificationRuleConfig incoming : incomingRules) {
            String ruleKey = safeText(incoming == null ? null : incoming.getRuleKey());
            if (!StringUtils.hasText(ruleKey)) {
                continue;
            }
            NotificationRuleConfig current = existingByKey.get(ruleKey);
            if (current == null) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Job is not seeded for this group.");
            }
            NotificationRuleConfig merged = current;
            mergeEditableFields(merged, incoming);
            if (!StringUtils.hasText(merged.getCreatedDate())) {
                merged.setCreatedDate(nowIso);
            }
            if (!StringUtils.hasText(merged.getCreatedUser())) {
                merged.setCreatedUser(firstNonBlank(actorUserId, SYSTEM_USER));
            }
            merged.setUpdatedDate(nowIso);
            merged.setUpdatedUser(firstNonBlank(actorUserId, SYSTEM_USER));
            saveEditableFields(merged);
        }
        return rules();
    }

    private NotificationRuleConfig saveEditableFields(NotificationRuleConfig rule) {
        Update update = new Update()
                .set("label", rule.getLabel())
                .set("category", rule.getCategory())
                .set("description", rule.getDescription())
                .set("actionKey", rule.getActionKey())
                .set("triggerKind", rule.getTriggerKind())
                .set("enabled", rule.getEnabled())
                .set("manualRunEnabled", rule.getManualRunEnabled())
                .set("adminManageable", rule.getAdminManageable())
                .set("priority", rule.getPriority())
                .set("channels", rule.getChannels())
                .set("timing", rule.getTiming())
                .set("scheduleSlots", rule.getScheduleSlots())
                .set("parameters", rule.getParameters())
                .set("message", rule.getMessage())
                .set("updatedDate", rule.getUpdatedDate())
                .set("updatedUser", rule.getUpdatedUser());
        return mongoTemplate.findAndModify(
                Query.query(ruleCriteria(safeText(rule.getRuleKey()))),
                update,
                FindAndModifyOptions.options().returnNew(true),
                NotificationRuleConfig.class);
    }

    private String i18nSegment(String value) {
        return safeText(value)
                .replaceAll("([a-z0-9])([A-Z])", "$1.$2")
                .replaceAll("[^a-zA-Z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "")
                .toLowerCase();
    }


    private void mergeEditableFields(NotificationRuleConfig target, NotificationRuleConfig source) {
        target.setRuleKey(firstNonBlank(source.getRuleKey(), target.getRuleKey()));
        target.setLabel(firstNonBlank(source.getLabel(), target.getLabel()));
        target.setCategory(firstNonBlank(source.getCategory(), target.getCategory()));
        target.setDescription(safeText(source.getDescription()));
        target.setActionKey(firstNonBlank(source.getActionKey(), target.getActionKey()));
        target.setTriggerKind(firstNonBlank(source.getTriggerKind(), target.getTriggerKind()));
        target.setEnabled(Boolean.TRUE.equals(source.getEnabled()));
        target.setManualRunEnabled(Boolean.TRUE.equals(source.getManualRunEnabled()));
        target.setAdminManageable(resolveAdminManageable(source, target));
        target.setPriority(valueOrDefault(source.getPriority(), valueOrDefault(target.getPriority(), 1000)));
        target.setChannels(normalizeChannels(source.getChannels()));
        target.setTiming(normalizeTiming(source.getTiming(), source.getTriggerKind()));
        target.setScheduleSlots(normalizeScheduleSlots(source.getScheduleSlots(), target.getTiming()));
        if (source.getParameters() != null && !source.getParameters().isEmpty()) {
            target.setParameters(normalizeParameters(source.getParameters()));
        }
        target.setMessage(normalizeMessage(source.getMessage()));
    }

    private List<NotificationRuleConfig.ParameterField> normalizeParameters(List<NotificationRuleConfig.ParameterField> source) {
        List<NotificationRuleConfig.ParameterField> fields = new ArrayList<>();
        for (NotificationRuleConfig.ParameterField field : source == null ? List.<NotificationRuleConfig.ParameterField>of() : source) {
            if (field == null) {
                continue;
            }
            String key = safeText(field.getKey());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            NotificationRuleConfig.ParameterField normalized = new NotificationRuleConfig.ParameterField();
            normalized.setKey(key);
            normalized.setLabel(firstNonBlank(field.getLabel(), key));
            normalized.setLabelKey(firstNonBlank(field.getLabelKey(), "admin.params.field." + key));
            normalized.setGroup(firstNonBlank(field.getGroup(), "General"));
            normalized.setGroupKey(firstNonBlank(field.getGroupKey(), "admin.params.group." + i18nSegment(field.getGroup())));
            String valueType = "text".equals(safeText(field.getValueType())) ? "text" : "number";
            normalized.setValueType(valueType);
            normalized.setNumberValue("number".equals(valueType) ? valueOrDefault(field.getNumberValue(), 0.0d) : null);
            normalized.setTextValue("text".equals(valueType) ? safeText(field.getTextValue()) : null);
            normalized.setUnit(safeText(field.getUnit()));
            normalized.setOptions(field.getOptions() == null ? List.of() : field.getOptions());
            normalized.setStrategy(safeText(field.getStrategy()));
            normalized.setStrategyKey(safeText(field.getStrategyKey()));
            normalized.setReadOnly(Boolean.TRUE.equals(field.getReadOnly()));
            fields.add(normalized);
        }
        return fields;
    }

    private NotificationRuleConfig.DeliveryChannels normalizeChannels(NotificationRuleConfig.DeliveryChannels source) {
        NotificationRuleConfig.DeliveryChannels channels = new NotificationRuleConfig.DeliveryChannels();
        channels.setPushEnabled(Boolean.TRUE.equals(source == null ? null : source.getPushEnabled()));
        channels.setEmailEnabled(Boolean.TRUE.equals(source == null ? null : source.getEmailEnabled()));
        channels.setInAppEnabled(Boolean.TRUE.equals(source == null ? null : source.getInAppEnabled()));
        channels.setSupportChatEnabled(Boolean.TRUE.equals(source == null ? null : source.getSupportChatEnabled()));
        return channels;
    }

    private Boolean resolveAdminManageable(NotificationRuleConfig source, NotificationRuleConfig target) {
        if (source != null && source.getAdminManageable() != null) {
            return Boolean.TRUE.equals(source.getAdminManageable());
        }
        if (target != null && target.getAdminManageable() != null) {
            return Boolean.TRUE.equals(target.getAdminManageable());
        }
        return false;
    }

    private NotificationRuleConfig.Timing normalizeTiming(NotificationRuleConfig.Timing source, String triggerKind) {
        NotificationRuleConfig.Timing timing = new NotificationRuleConfig.Timing();
        String mode = safeText(source == null ? null : source.getMode()).toLowerCase();
        if (!Set.of("immediate", "delay", "interval", "yearly", "manual").contains(mode)) {
            mode = "scheduled_process".equals(safeText(triggerKind)) ? "interval" : "immediate";
        }
        timing.setMode(mode);
        timing.setDelayMinutes(Math.max(0, valueOrDefault(source == null ? null : source.getDelayMinutes(), 0)));
        IntervalSpec interval = intervalSpec(
                source == null ? null : source.getIntervalAmount(),
                source == null ? null : source.getIntervalUnit(),
                source == null ? null : source.getIntervalSeconds(),
                source == null ? null : source.getIntervalMinutes());
        timing.setIntervalMinutes(interval.minutes());
        timing.setIntervalSeconds(interval.seconds());
        timing.setIntervalAmount(interval.amount());
        timing.setIntervalUnit(interval.unit());
        timing.setMonth(clamp(valueOrDefault(source == null ? null : source.getMonth(), 1), 1, 12));
        timing.setDayOfMonth(clamp(valueOrDefault(source == null ? null : source.getDayOfMonth(), 1), 1, 31));
        timing.setTime(normalizeTime(source == null ? null : source.getTime()));
        timing.setTimezone(firstNonBlank(source == null ? null : source.getTimezone(), "UTC"));
        timing.setCronExpression(firstNonBlank(
                source == null ? null : source.getCronExpression(),
                toIntervalExpression(interval, timing.getTime())));
        return timing;
    }

    private NotificationRuleConfig.MessageTemplate normalizeMessage(NotificationRuleConfig.MessageTemplate source) {
        NotificationRuleConfig.MessageTemplate message = new NotificationRuleConfig.MessageTemplate();
        message.setPushTitle(safeText(source == null ? null : source.getPushTitle()));
        message.setPushBody(safeText(source == null ? null : source.getPushBody()));
        message.setEmailTemplateKey(safeText(source == null ? null : source.getEmailTemplateKey()));
        message.setEmailSubject(safeText(source == null ? null : source.getEmailSubject()));
        message.setEmailBody(safeText(source == null ? null : source.getEmailBody()));
        message.setCtaPath(firstNonBlank(source == null ? null : source.getCtaPath(), "/game"));
        return message;
    }

    private List<NotificationRuleConfig.ScheduleSlot> normalizeScheduleSlots(
            List<NotificationRuleConfig.ScheduleSlot> source,
            NotificationRuleConfig.Timing fallbackTiming) {
        List<NotificationRuleConfig.ScheduleSlot> slots = new ArrayList<>();
        if (source != null) {
            int index = 0;
            for (NotificationRuleConfig.ScheduleSlot item : source) {
                if (item == null) {
                    continue;
                }
                NotificationRuleConfig.ScheduleSlot slot = new NotificationRuleConfig.ScheduleSlot();
                String frequency = normalizeFrequency(item.getFrequency());
                slot.setId(firstNonBlank(item.getId(), "run-window-" + (++index)));
                slot.setFrequency(frequency);
                slot.setDate(safeText(item.getDate()));
                slot.setDayOfWeek(clamp(valueOrDefault(item.getDayOfWeek(), 1), 1, 7));
                slot.setTime(normalizeTime(item.getTime()));
                slot.setTimezone(firstNonBlank(item.getTimezone(), "UTC"));
                slot.setCronExpression(firstNonBlank(item.getCronExpression(), toSlotCron(slot)));
                slot.setActionKey(safeText(item.getActionKey()));
                slot.setEnabled(!Boolean.FALSE.equals(item.getEnabled()));
                slots.add(slot);
            }
        }
        if (slots.isEmpty() && usesFallbackRunWindow(fallbackTiming)) {
            NotificationRuleConfig.ScheduleSlot slot = new NotificationRuleConfig.ScheduleSlot();
            slot.setId("run-window-default");
            slot.setFrequency("daily");
            slot.setDate("");
            slot.setDayOfWeek(1);
            slot.setTime(normalizeTime(fallbackTiming.getTime()));
            slot.setTimezone(firstNonBlank(fallbackTiming.getTimezone(), "UTC"));
            slot.setCronExpression(toSlotCron(slot));
            slot.setActionKey("");
            slot.setEnabled(true);
            slots.add(slot);
        }
        return slots;
    }

    private boolean usesFallbackRunWindow(NotificationRuleConfig.Timing timing) {
        if (timing == null) {
            return false;
        }
        String mode = safeText(timing.getMode()).toLowerCase();
        return !"interval".equals(mode) && !"yearly".equals(mode) && !"manual".equals(mode);
    }

    private List<NotificationRuleConfig> sorted(List<NotificationRuleConfig> rules) {
        return rules.stream()
                .filter(rule -> rule != null && StringUtils.hasText(safeText(rule.getRuleKey())))
                .sorted(Comparator
                        .comparing((NotificationRuleConfig rule) -> valueOrDefault(rule.getPriority(), 1000))
                        .thenComparing(rule -> safeText(rule.getRuleKey())))
                .toList();
    }

    private Instant yearlyOccurrence(NotificationRuleConfig.Timing timing, Instant now) {
        try {
            ZoneId zoneId = ZoneId.of(firstNonBlank(timing.getTimezone(), "UTC"));
            ZonedDateTime zonedNow = now.atZone(zoneId);
            int month = clamp(valueOrDefault(timing.getMonth(), 1), 1, 12);
            int dayOfMonth = clamp(valueOrDefault(timing.getDayOfMonth(), 1), 1, 31);
            LocalDate date = safeDate(zonedNow.getYear(), month, dayOfMonth);
            LocalTime time = LocalTime.parse(normalizeTime(timing.getTime()));
            return ZonedDateTime.of(date, time, zoneId).toInstant();
        } catch (Exception exception) {
            return null;
        }
    }

    private Instant slotOccurrence(NotificationRuleConfig.ScheduleSlot slot, Instant now) {
        if (slot == null || Boolean.FALSE.equals(slot.getEnabled())) {
            return null;
        }
        try {
            ZoneId zoneId = ZoneId.of(firstNonBlank(slot.getTimezone(), "UTC"));
            ZonedDateTime zonedNow = now.atZone(zoneId);
            LocalTime time = LocalTime.parse(normalizeTime(slot.getTime()));
            String frequency = normalizeFrequency(slot.getFrequency());
            if ("one-time".equals(frequency)) {
                LocalDate date = parseLocalDate(slot.getDate());
                return date == null ? null : ZonedDateTime.of(date, time, zoneId).toInstant();
            }
            if ("weekly".equals(frequency)) {
                int dayOfWeek = clamp(valueOrDefault(slot.getDayOfWeek(), 1), 1, 7);
                LocalDate date = zonedNow.toLocalDate().with(DayOfWeek.of(dayOfWeek));
                ZonedDateTime occurrence = ZonedDateTime.of(date, time, zoneId);
                if (occurrence.isAfter(zonedNow)) {
                    occurrence = occurrence.minusWeeks(1);
                }
                return occurrence.toInstant();
            }
            if ("bi-weekly".equals(frequency)) {
                int dayOfWeek = clamp(valueOrDefault(slot.getDayOfWeek(), 1), 1, 7);
                LocalDate anchor = parseLocalDate(slot.getDate());
                if (anchor == null) {
                    anchor = LocalDate.of(2026, 1, 5);
                }
                LocalDate date = zonedNow.toLocalDate().with(DayOfWeek.of(dayOfWeek));
                ZonedDateTime occurrence = ZonedDateTime.of(date, time, zoneId);
                if (occurrence.isAfter(zonedNow)) {
                    occurrence = occurrence.minusWeeks(1);
                }
                if (Math.floorMod(ChronoUnit.WEEKS.between(anchor.with(DayOfWeek.of(dayOfWeek)), occurrence.toLocalDate()), 2) != 0) {
                    occurrence = occurrence.minusWeeks(1);
                }
                return occurrence.toInstant();
            }
            if ("monthly".equals(frequency)) {
                int dayOfMonth = parseLocalDate(slot.getDate()) == null ? 1 : parseLocalDate(slot.getDate()).getDayOfMonth();
                LocalDate date = safeDate(zonedNow.getYear(), zonedNow.getMonthValue(), dayOfMonth);
                ZonedDateTime occurrence = ZonedDateTime.of(date, time, zoneId);
                if (occurrence.isAfter(zonedNow)) {
                    ZonedDateTime previousMonth = zonedNow.minusMonths(1);
                    occurrence = ZonedDateTime.of(safeDate(previousMonth.getYear(), previousMonth.getMonthValue(), dayOfMonth), time, zoneId);
                }
                return occurrence.toInstant();
            }
            if ("yearly".equals(frequency)) {
                LocalDate parsedDate = parseLocalDate(slot.getDate());
                int month = parsedDate == null ? 1 : parsedDate.getMonthValue();
                int dayOfMonth = parsedDate == null ? 1 : parsedDate.getDayOfMonth();
                LocalDate date = safeDate(zonedNow.getYear(), month, dayOfMonth);
                ZonedDateTime occurrence = ZonedDateTime.of(date, time, zoneId);
                if (occurrence.isAfter(zonedNow)) {
                    occurrence = ZonedDateTime.of(safeDate(zonedNow.getYear() - 1, month, dayOfMonth), time, zoneId);
                }
                return occurrence.toInstant();
            }
            ZonedDateTime occurrence = ZonedDateTime.of(zonedNow.toLocalDate(), time, zoneId);
            if (occurrence.isAfter(zonedNow)) {
                occurrence = occurrence.minusDays(1);
            }
            return occurrence.toInstant();
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isIntervalDue(NotificationRuleConfig.Timing timing, Instant lastRunAt, Instant now) {
        ZoneId zoneId;
        ZonedDateTime zonedNow;
        try {
            zoneId = ZoneId.of(firstNonBlank(timing == null ? null : timing.getTimezone(), "UTC"));
            zonedNow = now.atZone(zoneId);
            LocalTime startTime = LocalTime.parse(normalizeTime(timing == null ? null : timing.getTime()));
            if (zonedNow.toLocalTime().isBefore(startTime)) {
                return false;
            }
        } catch (DateTimeException exception) {
            return false;
        }
        if (lastRunAt == null) {
            return true;
        }
        IntervalSpec interval = intervalSpec(timing);
        ZonedDateTime base = lastRunAt.atZone(zoneId);
        if (INTERVAL_UNIT_MINUTES.equals(normalizeIntervalUnit(interval.unit()))) {
            base = base.truncatedTo(ChronoUnit.MINUTES);
        }
        ZonedDateTime due = plusInterval(base, interval);
        return !zonedNow.isBefore(due);
    }

    private LocalDate safeDate(int year, int month, int dayOfMonth) {
        int day = dayOfMonth;
        while (day >= 1) {
            try {
                return LocalDate.of(year, month, day);
            } catch (DateTimeException exception) {
                day -= 1;
            }
        }
        return LocalDate.of(year, month, 1);
    }

    private Instant parseInstant(String value) {
        String normalized = safeText(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (Exception exception) {
            return null;
        }
    }

    private NotificationRuleConfig.RunHistoryEntry runHistoryEntry(
            boolean manualRunEnabled,
            String startedAtIso,
            String finishedAtIso,
            long durationMillis,
            int processedCount,
            String status,
            String detail,
            String actorUserId) {
        NotificationRuleConfig.RunHistoryEntry entry = new NotificationRuleConfig.RunHistoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setTrigger(runTrigger(manualRunEnabled, actorUserId));
        entry.setRunnerUser(firstNonBlank(actorUserId, SYSTEM_USER));
        entry.setStartedAtIso(startedAtIso);
        entry.setFinishedAtIso(finishedAtIso);
        entry.setDurationMillis(durationMillis);
        entry.setProcessedCount(Math.max(0, processedCount));
        entry.setStatus(firstNonBlank(status, "completed"));
        entry.setDetail(safeText(detail));
        return entry;
    }

    private String runTrigger(NotificationRuleConfig rule, String actorUserId) {
        return runTrigger(rule != null && Boolean.TRUE.equals(rule.getManualRunEnabled()), actorUserId);
    }

    private String runTrigger(boolean manualRunEnabled, String actorUserId) {
        return manualRunEnabled && !"scheduler".equals(safeText(actorUserId))
                ? "manual"
                : "scheduled";
    }

    private String normalizeTerminalStatus(String status) {
        return switch (safeText(status).toLowerCase()) {
            case "failed" -> "failed";
            case "skipped" -> "skipped";
            default -> "completed";
        };
    }

    private IntervalSpec intervalSpec(NotificationRuleConfig.Timing timing) {
        return intervalSpec(
                timing == null ? null : timing.getIntervalAmount(),
                timing == null ? null : timing.getIntervalUnit(),
                timing == null ? null : timing.getIntervalSeconds(),
                timing == null ? null : timing.getIntervalMinutes());
    }

    private IntervalSpec intervalSpec(Integer amountValue, String unitValue, Integer secondsValue, Integer minutesValue) {
        int amount = Math.max(1, valueOrDefault(amountValue, 0));
        String unit = normalizeIntervalUnit(unitValue);
        if (valueOrDefault(amountValue, 0) > 0 && StringUtils.hasText(unit)) {
            int seconds = Math.max(1, amount * intervalUnitSeconds(unit));
            return new IntervalSpec(amount, unit, seconds, Math.max(1, (int) Math.ceil(seconds / 60.0d)));
        }
        int fallbackMinutes = intervalMinutesFromValues(secondsValue, minutesValue);
        int seconds = Math.max(1, valueOrDefault(secondsValue, fallbackMinutes * 60));
        String fallbackUnit = fixedIntervalUnit(seconds);
        int fallbackAmount = Math.max(1, seconds / intervalUnitSeconds(fallbackUnit));
        return new IntervalSpec(fallbackAmount, fallbackUnit, seconds, Math.max(1, (int) Math.ceil(seconds / 60.0d)));
    }

    private int intervalMinutesFromValues(Integer secondsValue, Integer minutesValue) {
        if (secondsValue != null && secondsValue > 0) {
            return Math.max(1, (int) Math.ceil(secondsValue / 60.0d));
        }
        return Math.max(1, valueOrDefault(minutesValue, 60));
    }

    private String normalizeIntervalUnit(String value) {
        return switch (safeText(value)) {
            case INTERVAL_UNIT_SECONDS,
                    INTERVAL_UNIT_MINUTES,
                    INTERVAL_UNIT_HOURS,
                    INTERVAL_UNIT_DAYS,
                    INTERVAL_UNIT_WEEKS,
                    INTERVAL_UNIT_MONTHS,
                    INTERVAL_UNIT_YEARS -> safeText(value);
            default -> "";
        };
    }

    private int intervalUnitSeconds(String unit) {
        return switch (normalizeIntervalUnit(unit)) {
            case INTERVAL_UNIT_SECONDS -> 1;
            case INTERVAL_UNIT_MINUTES -> 60;
            case INTERVAL_UNIT_HOURS -> 3600;
            case INTERVAL_UNIT_DAYS -> 86400;
            case INTERVAL_UNIT_WEEKS -> 604800;
            case INTERVAL_UNIT_MONTHS -> 2592000;
            case INTERVAL_UNIT_YEARS -> 31536000;
            default -> 60;
        };
    }

    private String fixedIntervalUnit(int seconds) {
        int value = Math.max(1, seconds);
        if (value % intervalUnitSeconds(INTERVAL_UNIT_YEARS) == 0) {
            return INTERVAL_UNIT_YEARS;
        }
        if (value % intervalUnitSeconds(INTERVAL_UNIT_MONTHS) == 0) {
            return INTERVAL_UNIT_MONTHS;
        }
        if (value % intervalUnitSeconds(INTERVAL_UNIT_WEEKS) == 0) {
            return INTERVAL_UNIT_WEEKS;
        }
        if (value % intervalUnitSeconds(INTERVAL_UNIT_DAYS) == 0) {
            return INTERVAL_UNIT_DAYS;
        }
        if (value % intervalUnitSeconds(INTERVAL_UNIT_HOURS) == 0) {
            return INTERVAL_UNIT_HOURS;
        }
        if (value % intervalUnitSeconds(INTERVAL_UNIT_MINUTES) == 0) {
            return INTERVAL_UNIT_MINUTES;
        }
        return INTERVAL_UNIT_SECONDS;
    }

    private ZonedDateTime plusInterval(ZonedDateTime base, IntervalSpec interval) {
        int amount = Math.max(1, interval == null ? 1 : interval.amount());
        return switch (interval == null ? INTERVAL_UNIT_MINUTES : normalizeIntervalUnit(interval.unit())) {
            case INTERVAL_UNIT_SECONDS -> base.plusSeconds(amount);
            case INTERVAL_UNIT_MINUTES -> base.plusMinutes(amount);
            case INTERVAL_UNIT_HOURS -> base.plusHours(amount);
            case INTERVAL_UNIT_DAYS -> base.plusDays(amount);
            case INTERVAL_UNIT_WEEKS -> base.plusWeeks(amount);
            case INTERVAL_UNIT_MONTHS -> base.plusMonths(amount);
            case INTERVAL_UNIT_YEARS -> base.plusYears(amount);
            default -> base.plusMinutes(amount);
        };
    }

    private String toIntervalExpression(IntervalSpec interval, String time) {
        int amount = Math.max(1, interval == null ? 60 : interval.amount());
        String unit = interval == null ? INTERVAL_UNIT_MINUTES : normalizeIntervalUnit(interval.unit());
        String[] timeParts = normalizeTime(time).split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);
        return switch (unit) {
            case INTERVAL_UNIT_SECONDS -> "0/" + amount + " * * * * ?";
            case INTERVAL_UNIT_MINUTES -> "0 0/" + amount + " * * * ?";
            case INTERVAL_UNIT_HOURS -> "0 " + minute + " 0/" + amount + " * * ?";
            case INTERVAL_UNIT_DAYS -> "0 " + minute + " " + hour + " 1/" + amount + " * ?";
            default -> "@every " + amount + " " + unit + " @ " + normalizeTime(time);
        };
    }

    private String toSlotCron(NotificationRuleConfig.ScheduleSlot slot) {
        String[] timeParts = normalizeTime(slot == null ? null : slot.getTime()).split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);
        String frequency = normalizeFrequency(slot == null ? null : slot.getFrequency());
        if ("one-time".equals(frequency)) {
            LocalDate date = parseLocalDate(slot == null ? null : slot.getDate());
            if (date == null) {
                return "0 " + minute + " " + hour + " * * ?";
            }
            return "0 " + minute + " " + hour + " " + date.getDayOfMonth() + " " + date.getMonthValue() + " ?";
        }
        if ("weekly".equals(frequency)) {
            int quartzDay = (clamp(valueOrDefault(slot == null ? null : slot.getDayOfWeek(), 1), 1, 7) % 7) + 1;
            return "0 " + minute + " " + hour + " ? * " + quartzDay;
        }
        if ("bi-weekly".equals(frequency)) {
            int quartzDay = (clamp(valueOrDefault(slot == null ? null : slot.getDayOfWeek(), 1), 1, 7) % 7) + 1;
            return "0 " + minute + " " + hour + " ? * " + quartzDay;
        }
        if ("monthly".equals(frequency)) {
            LocalDate date = parseLocalDate(slot == null ? null : slot.getDate());
            int dayOfMonth = date == null ? 1 : date.getDayOfMonth();
            return "0 " + minute + " " + hour + " " + dayOfMonth + " * ?";
        }
        if ("yearly".equals(frequency)) {
            LocalDate date = parseLocalDate(slot == null ? null : slot.getDate());
            int month = date == null ? 1 : date.getMonthValue();
            int dayOfMonth = date == null ? 1 : date.getDayOfMonth();
            return "0 " + minute + " " + hour + " " + dayOfMonth + " " + month + " ?";
        }
        return "0 " + minute + " " + hour + " * * ?";
    }

    private LocalDate parseLocalDate(String value) {
        try {
            return StringUtils.hasText(safeText(value)) ? LocalDate.parse(safeText(value)) : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String normalizeFrequency(String value) {
        String normalized = safeText(value).toLowerCase();
        return Set.of("one-time", "daily", "weekly", "bi-weekly", "monthly", "yearly").contains(normalized) ? normalized : "daily";
    }

    private String normalizeTime(String value) {
        String normalized = safeText(value);
        if (!normalized.matches("\\d{2}:\\d{2}")) {
            return "09:00";
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(safeText(first)) ? safeText(first) : safeText(second);
    }

    private int valueOrDefault(Integer value, int fallback) {
        return value == null ? fallback : value.intValue();
    }

    private double valueOrDefault(Double value, double fallback) {
        return value == null || !Double.isFinite(value) ? fallback : value.doubleValue();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
