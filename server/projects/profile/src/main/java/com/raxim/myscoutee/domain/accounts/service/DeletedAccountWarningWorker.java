package com.raxim.myscoutee.domain.accounts.service;

import com.raxim.myscoutee.domain.email.api.EmailRendering;

import com.raxim.myscoutee.domain.email.api.EmailSendResult;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;

import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.localization.api.TranslationBundles;
import com.raxim.myscoutee.domain.platform.api.SchedulingPolicy;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.email.api.EmailSending;

/** A deletion episode has one durable delivery claim, independent of subsequent Job runs. */
@Service
public class DeletedAccountWarningWorker {
    private final com.raxim.myscoutee.domain.accounts.service.AccountJobQueries jobGroupScope;

    public static final String RULE = "account-deletion-warning";
    public static final int WARNING_DAYS = 5;
    private static final int MAX_ATTEMPTS = 4; // Initial delivery plus three retries.
    private static final String COLLECTION = "accountDeletionWarnings";
    @Value("${email.account-warning-initial-retry-delay-seconds:60}")
    private long initialRetryDelaySeconds = 60;
    private final MongoTemplate mongo;
    private final SchedulingPolicy parameters;
    private final EmailSending email;
    private final NotificationJobs jobs;
    private final ScheduledMongoSessionRunner databases;
    private final TranslationBundles i18n;
    private final EmailRendering templates;
    public DeletedAccountWarningWorker(MongoTemplate mongo, SchedulingPolicy parameters,
            EmailSending email, NotificationJobs jobs, ScheduledMongoSessionRunner databases,
            TranslationBundles i18n, EmailRendering templates,
            com.raxim.myscoutee.domain.accounts.service.AccountJobQueries jobGroupScope) {
        this.jobGroupScope = jobGroupScope;

        this.mongo = mongo; this.parameters = parameters; this.email = email; this.jobs = jobs; this.databases = databases;
        this.i18n = i18n; this.templates = templates;
    }
    @Scheduled(fixedDelayString="${email.account-warning-check-delay-ms:60000}")
    public void tick() { databases.runAcrossConfiguredDatabases(this::run); }
    private void run() {
        var registry = jobs;
        if (registry != null) registry.forEachGroup(RULE, this::runInGroup);
    }

    private void runInGroup() {
        Instant now = Instant.now();
        boolean scanDue = jobs.shouldRunScheduledRule(RULE, now);
        if (!scanDue && (!email.enabled() || !mongo.exists(dueRetries(now), COLLECTION))) return;
        var run = scanDue ? jobs.beginScheduledRun(RULE, "account.deletion.warning.started", RULE, now)
                : jobs.beginQueuedRun(RULE, "account.deletion.warning.started", RULE, now);
        if (run == null) return;
        try {
            int sent = deliver(now);
            jobs.recordRun(RULE, sent, "completed", email.enabled() ? "account.deletion.warning.completed" : "operator.email.notConfigured", RULE, now);
        } catch (RuntimeException failure) {
            jobs.recordRun(RULE, failure instanceof DeliveryFailure delivery ? delivery.acceptedCount : 0,
                    "failed", "account.deletion.warning.failed", RULE, now);
            throw failure;
        }
    }
    private Query dueRetries(Instant now) {
        return Query.query(Criteria.where("baseGroupId").is(JobGroupContext.groupId()).and("status").is("retry")
                .and("nextAttemptAtIso").lte(now.toString()).and("attemptCount").lt(MAX_ATTEMPTS));
    }
    public int deliver(Instant now) {
        if (!email.enabled()) return 0;
        int graceDays = parameters.accountPurgeWindowDays(30);
        if (graceDays < WARNING_DAYS) return 0;
        // A timed-out/crashed request may already have been accepted by the provider. Never resend it blindly.
        mongo.updateMulti(Query.query(Criteria.where("baseGroupId").is(JobGroupContext.groupId()).and("status").is("sending").and("startedAtIso").lt(now.minusSeconds(120).toString())),
                new Update().set("status", "unknown").set("error", "Delivery outcome is unknown; automatic resend suppressed."), COLLECTION);
        Query candidates = Query.query(Criteria.where("profileStatus").is("deleted").and("deletedAtIso")
                .gt(now.minus(graceDays, ChronoUnit.DAYS).toString())
                .lte(now.minus(graceDays - WARNING_DAYS, ChronoUnit.DAYS).toString())
                .andOperator(Criteria.where("$expr").is(new Document("$ne", java.util.List.of("$deletedAtIso",
                        new Document("$ifNull", java.util.List.of("$deletionWarningFor", "")))))))
                .with(Sort.by("deletedAtIso", "userId")).limit(100);
        candidates.addCriteria(jobGroupScope.workspaceCriteria("workspaceGroupId"));
        int sent = 0, failed = 0;
        var bundle = i18n.getBundle("en", "en", null).data();
        if (bundle == null || !bundle.containsKey("account.deletion.warning.subject") || !bundle.containsKey("account.deletion.warning.body"))
            throw new IllegalStateException("Account deletion email translations are missing.");
        Query retries = dueRetries(now)
                .with(Sort.by("nextAttemptAtIso", "_id")).limit(100);
        for (Document retry : mongo.find(retries, Document.class, COLLECTION)) {
            Document claim = mongo.findAndModify(Query.query(Criteria.where("_id").is(retry.getString("_id"))
                            .and("status").is("retry").and("nextAttemptAtIso").lte(now.toString())
                            .and("attemptCount").is(retry.getInteger("attemptCount"))),
                    new Update().set("status", "sending").set("startedAtIso", now.toString())
                            .inc("attemptCount", 1).unset("nextAttemptAtIso"),
                    FindAndModifyOptions.options().returnNew(true), Document.class, COLLECTION);
            if (claim == null) continue;
            int outcome = deliverClaim(claim, bundle, now, graceDays);
            if (outcome > 0) sent++; else if (outcome < 0) failed++;
        }
        for (Users user : mongo.find(candidates, Users.class)) {
            String episode = user.getDeletedAtIso(), id = user.getUserId() + ":" + episode;
            var existing = mongo.findById(id, Document.class, COLLECTION);
            if (existing != null) {
                if (!"sending".equals(existing.getString("status"))) markHandled(user.getUserId(), episode);
                continue;
            }
            var claim = new Document("_id", id).append("baseGroupId", JobGroupContext.groupId()).append("userId", user.getUserId()).append("deletedAtIso", episode)
                    .append("status", "sending").append("startedAtIso", now.toString()).append("attemptCount", 1);
            try { mongo.insert(claim, COLLECTION); } catch (DuplicateKeyException duplicate) { continue; }
            int outcome = deliverClaim(claim, bundle, now, graceDays);
            if (outcome > 0) sent++; else if (outcome < 0) failed++;
        }
        if (failed > 0) throw new DeliveryFailure(sent);
        return sent;
    }

    private static final class DeliveryFailure extends IllegalStateException {
        private final int acceptedCount;

        private DeliveryFailure(int acceptedCount) {
            super("Some deletion warnings were not accepted by the email provider.");
            this.acceptedCount = acceptedCount;
        }
    }
    private int deliverClaim(Document claim, Map<String, String> bundle, Instant now, int graceDays) {
        String id = claim.getString("_id"), userId = claim.getString("userId"), episode = claim.getString("deletedAtIso");
        Instant purgeAt = Instant.parse(episode).plus(graceDays, ChronoUnit.DAYS);
        // Recheck restoration and the purge deadline on every attempt, including retries.
        Users current = purgeAt.isAfter(now) ? mongo.findOne(Query.query(Criteria.where("userId").is(userId)
                .and("profileStatus").is("deleted").and("deletedAtIso").is(episode)), Users.class) : null;
        String status = "cancelled", providerId = null;
        Instant nextAttempt = null;
        int outcome = 0;
        if (current != null) {
            var variables = Map.of("name", current.getName() == null ? "" : current.getName(), "purgeAt", purgeAt.toString());
            EmailSendResult result;
            try {
                result = email.send(new EmailSendRequest(current.getEmail(), current.getName(), bundle.get("account.deletion.warning.subject"), "",
                        templates.renderText(bundle.get("account.deletion.warning.body"), variables), "", "", "", RULE, Map.of("deletionEpisode", id)));
            } catch (RuntimeException error) {
                result = EmailSendResult.unknown("Email delivery outcome is unknown.");
            }
            status = result.suppressed() ? "unsubscribed" : result.accepted() ? "sent" : result.outcomeUnknown() ? "unknown" : "failed";
            providerId = result.providerMessageId();
            outcome = result.suppressed() ? 0 : result.accepted() ? 1 : -1;
            int attempts = claim.getInteger("attemptCount");
            if (result.retryable() && attempts < MAX_ATTEMPTS) {
                // One, two and four minute minimum delays, or longer when the provider requests it.
                Instant finished = Instant.now();
                long remaining = Math.max(0, java.time.Duration.between(finished, purgeAt).toSeconds());
                long baseDelay = Math.min(Math.max(1, initialRetryDelaySeconds), remaining);
                long delay = Math.max(baseDelay << (attempts - 1), result.retryAfterSeconds());
                nextAttempt = finished.plusSeconds(Math.min(delay, remaining));
                status = "retry";
            }
        }
        Update update = new Update().set("status", status).set("providerMessageId", providerId)
                .set("finishedAtIso", Instant.now().toString());
        if (nextAttempt == null) update.unset("nextAttemptAtIso");
        else update.set("nextAttemptAtIso", nextAttempt.toString());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id).and("status").is("sending")
                .and("attemptCount").is(claim.getInteger("attemptCount"))), update, COLLECTION);
        // The episode has a durable claim. Pending retries are selected from that claim, not the user scan.
        markHandled(userId, episode);
        return outcome;
    }

    private void markHandled(String userId, String episode) {
        mongo.updateFirst(Query.query(Criteria.where("userId").is(userId).and("deletedAtIso").is(episode)),
                new Update().set("deletionWarningFor", episode), Users.class);
    }
}
