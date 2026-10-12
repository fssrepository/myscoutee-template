package com.raxim.myscoutee.microservices.transport.notifications;

import java.net.URI;
import java.time.*;
import java.util.*;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.notifications.dto.JobRuleDto;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Callbacks inherit the canonical local orchestration; only owner state crosses HTTP. No retries. */
public final class RemoteNotificationJobs extends RemoteReadTransport implements NotificationJobs {
    private final ObjectMapper mapper;
    public RemoteNotificationJobs(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public List<String> configuredGroupIds(Set<String> ruleKeys) {
        var query = new NotificationJobsProtocol.GroupsQuery(ruleKeys);
        return exchange(NotificationJobsProtocol.GROUPS, body(query, NotificationJobsProtocol.valid(query)),
                new TypeReference<List<String>>() {}, "jobs.groups", NotificationJobsProtocol.MAX_RESPONSE_BYTES,
                values -> values.stream().allMatch(value -> value == null || value.length() <= 512));
    }
    @Override public Optional<JobRuleDto> findRule(String ruleKey) {
        var query = new NotificationJobsProtocol.RuleQuery(NotificationJobsProtocol.Scope.current(), ruleKey);
        return Optional.ofNullable(rule(NotificationJobsProtocol.FIND, query, NotificationJobsProtocol.valid(query)));
    }
    @Override public boolean shouldRunScheduledRule(String ruleKey, Instant now) {
        var query = new NotificationJobsProtocol.DueQuery(NotificationJobsProtocol.Scope.current(), ruleKey, now);
        return decision(NotificationJobsProtocol.DUE, query, NotificationJobsProtocol.valid(query));
    }

    @Override public JobRuleDto beginRun(String ruleKey, String detail, String actorUserId) { return beginRun(ruleKey, detail, actorUserId, null); }
    @Override public JobRuleDto recordRun(String ruleKey, int count, String status, String detail, String actorUserId) {
        return recordRun(ruleKey, count, status, detail, actorUserId, null);
    }
    @Override public JobRuleDto beginRun(String ruleKey, String detail, String actorUserId, Instant startedAt) {
        var command = new NotificationJobsProtocol.BeginCommand(NotificationJobsProtocol.Scope.current(), ruleKey, detail, actorUserId, startedAt);
        return rule(NotificationJobsProtocol.BEGIN, command, NotificationJobsProtocol.valid(command));
    }
    @Override public JobRuleDto beginScheduledRun(String ruleKey, String detail, String actorUserId, Instant startedAt) {
        var command = new NotificationJobsProtocol.BeginCommand(NotificationJobsProtocol.Scope.current(), ruleKey, detail, actorUserId, startedAt);
        return rule(NotificationJobsProtocol.SCHEDULED, command, NotificationJobsProtocol.valid(command));
    }
    @Override public JobRuleDto beginQueuedRun(String ruleKey, String detail, String actorUserId, Instant startedAt) {
        var command = new NotificationJobsProtocol.BeginCommand(NotificationJobsProtocol.Scope.current(), ruleKey, detail, actorUserId, startedAt);
        return rule(NotificationJobsProtocol.QUEUED, command, NotificationJobsProtocol.valid(command));
    }
    @Override public JobRuleDto recordRun(String ruleKey, int count, String status, String detail, String actorUserId, Instant startedAt) {
        var command = new NotificationJobsProtocol.RecordCommand(NotificationJobsProtocol.Scope.current(), ruleKey, count, status, detail, actorUserId, startedAt);
        return rule(NotificationJobsProtocol.RECORD, command, NotificationJobsProtocol.valid(command));
    }
    @Override public JobRuleDto adjustPendingCount(String ruleKey, int delta, String unit) {
        var command = new NotificationJobsProtocol.PendingCommand(NotificationJobsProtocol.Scope.current(), ruleKey, delta, unit);
        return rule(NotificationJobsProtocol.ADJUST, command, NotificationJobsProtocol.valid(command));
    }
    @Override public int claimPendingCount(String ruleKey) {
        throw new IllegalArgumentException("Remote pending claims require the acquired run's startedAt");
    }
    @Override public int claimPendingCount(String ruleKey, Instant startedAt) {
        var query = new NotificationJobsProtocol.Claim(NotificationJobsProtocol.Scope.current(), ruleKey, startedAt);
        return exchange(NotificationJobsProtocol.CLAIM, body(query, NotificationJobsProtocol.valid(query)),
                new TypeReference<NotificationJobsProtocol.Count>() {}, "jobs.claim-pending", 16384,
                result -> result.value() != null && result.value() >= 0).value();
    }
    @Override public void restoreClaimedPendingCount(String ruleKey, int claimedCount, String unit) {
        throw new IllegalArgumentException("Remote pending restoration requires the acquired run's startedAt");
    }
    @Override public void restoreClaimedPendingCount(String ruleKey, int claimedCount, String unit, Instant startedAt) {
        var command = new NotificationJobsProtocol.Restore(NotificationJobsProtocol.Scope.current(), ruleKey, claimedCount, unit, startedAt);
        exchange(NotificationJobsProtocol.RESTORE, body(command, NotificationJobsProtocol.valid(command)),
                new TypeReference<NotificationJobsProtocol.Receipt>() {}, "jobs.restore-pending", 16384,
                result -> Boolean.TRUE.equals(result.completed()));
    }
    private JobRuleDto rule(String path, Object query, boolean valid) {
        return exchange(path, body(query, valid), new TypeReference<NotificationJobsProtocol.RuleResult>() {},
                "jobs.rule-state", NotificationJobsProtocol.MAX_RESPONSE_BYTES, result -> Boolean.TRUE.equals(result.resolved())).value();
    }
    private boolean decision(String path, Object query, boolean valid) {
        return exchange(path, body(query, valid), new TypeReference<NotificationJobsProtocol.Decision>() {},
                "jobs.decision", 16384, result -> result.value() != null).value();
    }
    private byte[] body(Object value, boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid job operation");
        byte[] bytes = mapper.writeValueAsBytes(value);
        if (bytes.length > NotificationJobsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Job operation exceeds byte limit");
        return bytes;
    }
}
