package com.raxim.myscoutee.domain.notifications.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

/** Owner-backed scheduler state; callbacks execute locally in their scoped business service. */
public interface NotificationJobs {
    String RULE_NOTIFICATION_OUTBOX = "notification-outbox";
    String RULE_SCHEDULED_MESSAGES = "scheduled-messages";
    String RULE_ACCOUNT_PURGE = "account-purge";
    String PENDING_COUNT_UNIT_USERS = "admin.jobs.table.users";

    List<String> configuredGroupIds(Set<String> ruleKeys);
    default void forEachGroup(String ruleKey, Runnable action) {
        forEachGroup(Set.of(ruleKey), action);
    }

    default void forEachGroup(Set<String> ruleKeys, Runnable action) {
        var groups = configuredGroupIds(ruleKeys);
        RuntimeException failure = null;
        for (String groupId : groups) {
            try { JobGroupContext.run(groupId, action); }
            catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    /** Existing scheduled processors share catalog/claim/history behavior, with one execution per seeded group. */
    default void runScheduledGroups(String ruleKey, Runnable process) {
        forEachGroup(ruleKey, () -> runScheduledGroup(ruleKey, process));
    }

    /** Reuse admission/history after an owner has found work in the current group. */
    default void runScheduledGroup(String ruleKey, Runnable process) {
        Instant started = Instant.now();
        // The owner checks the schedule and atomically claims the run in this call.
        // A separate due lookup repeats the same read (and HTTP round trip remotely).
        if (beginScheduledRun(ruleKey, "Scheduled processing started.", ruleKey, started) == null) return;
        try {
            process.run();
            recordRun(ruleKey, 0, "completed", "Scheduled processing completed.", ruleKey, started);
        } catch (RuntimeException error) {
            recordRun(ruleKey, 0, "failed", error.getMessage(), ruleKey, started);
            throw error;
        }
    }


    Optional<? extends Configuration> findRule(String ruleKey);
    boolean shouldRunScheduledRule(String ruleKey, Instant now);
    Configuration beginRun(String ruleKey, String detail, String actorUserId);
    Configuration beginRun(String ruleKey, String detail, String actorUserId, Instant startedAt);
    Configuration beginScheduledRun(String ruleKey, String detail, String actorUserId, Instant startedAt);
    Configuration beginQueuedRun(String ruleKey, String detail, String actorUserId, Instant startedAt);
    Configuration recordRun(String ruleKey, int count, String status, String detail, String actorUserId);
    Configuration recordRun(String ruleKey, int count, String status, String detail, String actorUserId, Instant startedAt);
    Configuration adjustPendingCount(String ruleKey, int delta, String unit);
    int claimPendingCount(String ruleKey);
    int claimPendingCount(String ruleKey, Instant startedAt);
    void restoreClaimedPendingCount(String ruleKey, int claimedCount, String unit);
    void restoreClaimedPendingCount(String ruleKey, int claimedCount, String unit, Instant startedAt);

    interface Configuration {
        Boolean getEnabled();
        List<? extends Parameter> getParameters();
    }
    interface Parameter {
        String getKey();
        String getTextValue();
    }
}
