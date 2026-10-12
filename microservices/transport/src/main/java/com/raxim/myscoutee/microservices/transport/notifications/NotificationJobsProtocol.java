package com.raxim.myscoutee.microservices.transport.notifications;

import java.time.Instant;
import java.util.Set;
import com.raxim.myscoutee.domain.notifications.dto.JobRuleDto;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

public final class NotificationJobsProtocol {
    public static final String ROOT = "/_internal/v1/notifications/jobs";
    public static final String GROUPS = ROOT + "/groups", FIND = ROOT + "/rule", DUE = ROOT + "/due",
            BEGIN = ROOT + "/begin", SCHEDULED = ROOT + "/begin-scheduled", QUEUED = ROOT + "/begin-queued", RECORD = ROOT + "/record",
            ADJUST = ROOT + "/adjust-pending", CLAIM = ROOT + "/claim-pending", RESTORE = ROOT + "/restore-pending";
    public static final int MAX_REQUEST_BYTES = 512 * 1024, MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private NotificationJobsProtocol() {}
    public static String[] paths() { return new String[]{GROUPS, FIND, DUE, BEGIN, SCHEDULED, QUEUED, RECORD, ADJUST, CLAIM, RESTORE}; }
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(JobGroupContext.isActive(), JobGroupContext.groupId()); }
    }
    public record GroupsQuery(Set<String> ruleKeys) {}
    public record RuleQuery(Scope scope, String ruleKey) {}
    public record DueQuery(Scope scope, String ruleKey, Instant now) {}
    public record ScopeQuery(Scope scope) {}
    public record BeginCommand(Scope scope, String ruleKey, String detail, String actorUserId, Instant startedAt) {}
    public record RecordCommand(Scope scope, String ruleKey, Integer count, String status, String detail, String actorUserId, Instant startedAt) {}
    public record PendingCommand(Scope scope, String ruleKey, Integer count, String unit) {}
    public record Claim(Scope scope, String ruleKey, Instant startedAt) {}
    public record Restore(Scope scope, String ruleKey, Integer count, String unit, Instant startedAt) {}
    public record RuleResult(Boolean resolved, JobRuleDto value) {}
    public record Decision(Boolean value) {}
    public record Count(Integer value) {}
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    private static boolean text(String value, int size) { return value == null || value.length() <= size; }
    private static boolean scope(Scope value) { return value != null && value.active() != null && text(value.groupId(), 512) && (value.active() || value.groupId() == null); }
    public static boolean valid(GroupsQuery q) { return q != null && q.ruleKeys() != null && q.ruleKeys().size() <= 128 && q.ruleKeys().stream().allMatch(key -> text(key, 512)); }
    public static boolean valid(RuleQuery q) { return q != null && scope(q.scope()) && text(q.ruleKey(), 512); }
    public static boolean valid(DueQuery q) { return q != null && scope(q.scope()) && text(q.ruleKey(), 512); }
    public static boolean valid(ScopeQuery q) { return q != null && scope(q.scope()); }
    public static boolean valid(BeginCommand q) { return q != null && scope(q.scope()) && text(q.ruleKey(), 512) && text(q.detail(), 8192) && text(q.actorUserId(), 512); }
    public static boolean valid(RecordCommand q) { return q != null && scope(q.scope()) && text(q.ruleKey(), 512) && q.count() != null && text(q.status(), 512) && text(q.detail(), 8192) && text(q.actorUserId(), 512); }
    public static boolean valid(Claim q) { return q != null && scope(q.scope()) && text(q.ruleKey(), 512) && q.startedAt() != null; }
    public static boolean valid(Restore q) { return q != null && scope(q.scope()) && text(q.ruleKey(), 512) && q.count() != null && text(q.unit(), 512) && q.startedAt() != null; }
    public static boolean valid(PendingCommand q) { return q != null && scope(q.scope()) && text(q.ruleKey(), 512) && q.count() != null && text(q.unit(), 512); }
}
