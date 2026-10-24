package com.raxim.myscoutee.microservices.transport.notifications;

import java.util.function.*;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.notifications.dto.JobRuleDto;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class NotificationJobsEndpoint {
    private final NotificationJobs owner;
    private final ObjectMapper mapper;
    public NotificationJobsEndpoint(NotificationJobs owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = NotificationJobsProtocol.GROUPS, produces = "application/json")
    public ResponseEntity<?> groups(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.GroupsQuery.class, NotificationJobsProtocol::valid,
                q -> new NotificationJobsProtocol.Scope(false, null), q -> owner.configuredGroupIds(q.ruleKeys()));
    }
    @PostMapping(value = NotificationJobsProtocol.FIND, produces = "application/json")
    public ResponseEntity<?> find(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.RuleQuery.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.RuleQuery::scope, q -> new NotificationJobsProtocol.RuleResult(true, owner.findRule(q.ruleKey()).map(JobRuleDto::from).orElse(null)));
    }
    @PostMapping(value = NotificationJobsProtocol.DUE, produces = "application/json")
    public ResponseEntity<?> due(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.DueQuery.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.DueQuery::scope, q -> new NotificationJobsProtocol.Decision(owner.shouldRunScheduledRule(q.ruleKey(), q.now())));
    }

    @PostMapping(value = NotificationJobsProtocol.BEGIN, produces = "application/json")
    public ResponseEntity<?> begin(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.BeginCommand.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.BeginCommand::scope, q -> new NotificationJobsProtocol.RuleResult(true, JobRuleDto.from(owner.beginRun(q.ruleKey(), q.detail(), q.actorUserId(), q.startedAt()))));
    }
    @PostMapping(value = NotificationJobsProtocol.SCHEDULED, produces = "application/json")
    public ResponseEntity<?> scheduled(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.BeginCommand.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.BeginCommand::scope, q -> new NotificationJobsProtocol.RuleResult(true, JobRuleDto.from(owner.beginScheduledRun(q.ruleKey(), q.detail(), q.actorUserId(), q.startedAt()))));
    }
    @PostMapping(value = NotificationJobsProtocol.QUEUED, produces = "application/json")
    public ResponseEntity<?> queued(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.BeginCommand.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.BeginCommand::scope, q -> new NotificationJobsProtocol.RuleResult(true, JobRuleDto.from(owner.beginQueuedRun(q.ruleKey(), q.detail(), q.actorUserId(), q.startedAt()))));
    }
    @PostMapping(value = NotificationJobsProtocol.RECORD, produces = "application/json")
    public ResponseEntity<?> record(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.RecordCommand.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.RecordCommand::scope, q -> new NotificationJobsProtocol.RuleResult(true, JobRuleDto.from(owner.recordRun(q.ruleKey(), q.count(), q.status(), q.detail(), q.actorUserId(), q.startedAt()))));
    }
    @PostMapping(value = NotificationJobsProtocol.ADJUST, produces = "application/json")
    public ResponseEntity<?> adjust(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.PendingCommand.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.PendingCommand::scope, q -> new NotificationJobsProtocol.RuleResult(true, JobRuleDto.from(owner.adjustPendingCount(q.ruleKey(), q.count(), q.unit()))));
    }
    @PostMapping(value = NotificationJobsProtocol.CLAIM, produces = "application/json")
    public ResponseEntity<?> claim(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.Claim.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.Claim::scope, q -> new NotificationJobsProtocol.Count(owner.claimPendingCount(q.ruleKey(), q.startedAt())));
    }
    @PostMapping(value = NotificationJobsProtocol.RESTORE, produces = "application/json")
    public ResponseEntity<?> restore(HttpServletRequest request) throws java.io.IOException {
        return call(request, NotificationJobsProtocol.Restore.class, NotificationJobsProtocol::valid,
                NotificationJobsProtocol.Restore::scope, q -> { owner.restoreClaimedPendingCount(q.ruleKey(), q.count(), q.unit(), q.startedAt()); return new NotificationJobsProtocol.Receipt(true); });
    }
    private <T> ResponseEntity<?> call(HttpServletRequest request, Class<T> type, Predicate<T> valid,
            Function<T, NotificationJobsProtocol.Scope> scope, Function<T, Object> action) throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, NotificationJobsProtocol.MAX_REQUEST_BYTES);
            T query = ReadQueryBodies.decode(bytes, mapper, type, valid, false);
            var selected = scope.apply(query);
            return response(200, JobGroupContext.call(selected.active(), selected.groupId(), () -> action.apply(query)));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new NotificationJobsProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new NotificationJobsProtocol.Error("JOB_STATE_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object value) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(value); }
}
