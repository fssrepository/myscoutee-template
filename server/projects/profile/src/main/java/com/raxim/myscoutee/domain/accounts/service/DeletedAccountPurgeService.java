package com.raxim.myscoutee.domain.accounts.service;

import com.raxim.myscoutee.domain.media.api.MediaCleanup;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.domain.platform.api.SchedulingPolicy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.accounts.document.AccountPurgeJob;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.AccountPurgeJobsRepository;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;

@Service
public class DeletedAccountPurgeService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.raxim.myscoutee.domain.notifications.api.NotificationPrivacy notificationPrivacy;
    @org.springframework.beans.factory.annotation.Autowired
    private com.raxim.myscoutee.domain.help.api.HelpPrivacy helpPrivacy;

    @org.springframework.beans.factory.annotation.Autowired
    private com.raxim.myscoutee.domain.platform.api.JobAudience jobGroupScope;

    @org.springframework.beans.factory.annotation.Autowired
    private com.raxim.myscoutee.domain.integrations.api.IntegrationPrivacy integrationPrivacy;


    private static final int DELETE_GRACE_DAYS = 30;
    private static final Duration PURGE_JOB_LEASE = Duration.ofMinutes(30);
    private static final Duration PURGE_RETRY_BASE = Duration.ofMinutes(15);
    private static final Duration PURGE_RETRY_MAX = Duration.ofHours(24);
    private static final String PROFILE_STATUS_DELETED = "deleted";
    private static final String PROFILE_STATUS_ONBOARDING = "onboarding";

    private final UsersRepository usersRepository;
    private final com.raxim.myscoutee.domain.chat.api.ChatPrivacy chatPrivacy;
    private final AccountPurgeJobsRepository accountPurgeJobsRepository;
    private final MediaCleanup mediaStorageService;
    private final MongoTemplate mongoTemplate;
    private final SchedulingPolicy platformParametersService;
    private NotificationJobs notificationRuleRegistryService;
    private ScheduledMongoSessionRunner scheduledMongoSessionRunner;

    public DeletedAccountPurgeService(
            UsersRepository usersRepository,
            com.raxim.myscoutee.domain.chat.api.ChatPrivacy chatPrivacy,
            AccountPurgeJobsRepository accountPurgeJobsRepository,
            MediaCleanup mediaStorageService,
            MongoTemplate mongoTemplate,
            SchedulingPolicy platformParametersService) {
        this.usersRepository = usersRepository;
        this.chatPrivacy = chatPrivacy;
        this.accountPurgeJobsRepository = accountPurgeJobsRepository;
        this.mediaStorageService = mediaStorageService;
        this.mongoTemplate = mongoTemplate;
        this.platformParametersService = platformParametersService;
    }

    @Autowired(required = false)
    void setNotificationRuleRegistryService(NotificationJobs notificationRuleRegistryService) {
        this.notificationRuleRegistryService = notificationRuleRegistryService;
    }

    @Autowired(required = false)
    void setScheduledMongoSessionRunner(ScheduledMongoSessionRunner scheduledMongoSessionRunner) {
        this.scheduledMongoSessionRunner = scheduledMongoSessionRunner;
    }

    @Scheduled(fixedDelayString = "${users.deleted-account-purge-delay-ms:3600000}")
    public void purgeExpiredDeletedAccounts() {
        if (scheduledMongoSessionRunner == null) {
            purgeExpiredDeletedAccountsForCurrentDatabase();
            return;
        }
        scheduledMongoSessionRunner.runAcrossConfiguredDatabases(this::purgeExpiredDeletedAccountsForCurrentDatabase);
    }

    private void purgeExpiredDeletedAccountsForCurrentDatabase() {
        var registry = notificationRuleRegistryService;
        if (registry != null) registry.forEachGroup(NotificationJobs.RULE_ACCOUNT_PURGE, this::purgeExpiredDeletedAccountsForCurrentDatabaseInGroup);
    }

    private void purgeExpiredDeletedAccountsForCurrentDatabaseInGroup() {
        Instant now = Instant.now();
        boolean ruleConfigured = notificationRuleRegistryService != null
                && notificationRuleRegistryService
                        .findRule(NotificationJobs.RULE_ACCOUNT_PURGE)
                        .isPresent();
        if (ruleConfigured && !notificationRuleRegistryService.shouldRunScheduledRule(
                NotificationJobs.RULE_ACCOUNT_PURGE,
                now)) {
            return;
        }
        if (ruleConfigured && notificationRuleRegistryService.beginScheduledRun(
                    NotificationJobs.RULE_ACCOUNT_PURGE,
                    "Scheduled deleted-account purge started.",
                    "account-purge",
                    now) == null) {
            return;
        }
        try {
            String purgeBeforeIso = now.minus(deleteGraceDays(), ChronoUnit.DAYS).toString();
            int purgedCount = purgeExpiredUsers(PROFILE_STATUS_DELETED, purgeBeforeIso, now)
                    + purgeExpiredUsers(PROFILE_STATUS_ONBOARDING, purgeBeforeIso, now)
                    + resumeDuePurgeJobs(now);
            if (ruleConfigured) {
                notificationRuleRegistryService.recordRun(
                        NotificationJobs.RULE_ACCOUNT_PURGE,
                        purgedCount,
                        "completed",
                        "Processed scheduled deleted-account purge work.",
                        "account-purge",
                        now);
            }
        } catch (RuntimeException exception) {
            if (ruleConfigured) {
                notificationRuleRegistryService.recordRun(
                        NotificationJobs.RULE_ACCOUNT_PURGE,
                        0,
                        "failed",
                        safeText(exception.getMessage()),
                        "account-purge",
                        now);
            }
            throw exception;
        }
    }

    private int purgeExpiredUsers(String profileStatus, String purgeBeforeIso, Instant now) {
        int purgedCount = 0;
        List<Users> users = usersRepository.findByProfileStatusAndDeletedAtIsoLessThanEqual(profileStatus, purgeBeforeIso);
        for (Users user : users == null ? List.<Users>of() : users) {
            if (user != null && JobGroupContext.isActive() && !jobGroupScope.matchesWorkspace(user.getWorkspaceGroupId())) continue;
            if (purgeUser(user, null, now)) {
                purgedCount += 1;
            }
        }
        return purgedCount;
    }

    private int deleteGraceDays() {
        return platformParametersService == null
                ? DELETE_GRACE_DAYS
                : platformParametersService.accountPurgeWindowDays(DELETE_GRACE_DAYS);
    }

    private int resumeDuePurgeJobs(Instant now) {
        int purgedCount = 0;
        List<AccountPurgeJob> jobs = accountPurgeJobsRepository.findByStatusIn(List.of(
                AccountPurgeJob.STATUS_PENDING,
                AccountPurgeJob.STATUS_RUNNING,
                AccountPurgeJob.STATUS_FAILED));
        for (AccountPurgeJob job : jobs == null ? List.<AccountPurgeJob>of() : jobs) {
            if (job != null && !java.util.Objects.equals(job.getBaseGroupId(), JobGroupContext.groupId())) continue;
            String userId = safeText(job == null ? null : job.getUserId());
            if (!StringUtils.hasText(userId) || !shouldRunPurgeJob(job, now)) {
                continue;
            }
            Users user = usersRepository.findById(userId).orElse(null);
            if (user == null) {
                completeJob(job, now);
                continue;
            }
            if (purgeUser(user, job, now)) {
                purgedCount += 1;
            }
        }
        return purgedCount;
    }

    private boolean purgeUser(Users user, AccountPurgeJob existingJob, Instant now) {
        String userId = user == null ? "" : safeText(user.getId());
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        AccountPurgeJob job = existingJob == null ? loadOrCreatePurgeJob(userId, now) : existingJob;
        if (!shouldRunPurgeJob(job, now)) {
            return false;
        }
        try {
            startJob(job, userId, now);
            requireRecordedCleanupOwners(job);

            markStage(job, "profile-media");
            purgeUserMedia(user);

            markStage(job, "chat-activity");
            chatPrivacy.purgeUserChatActivity(userId);

            markStage(job, "referenced-documents");
            purgeUserReferencedDocuments(userId);

            markStage(job, "delete-user");
            boolean pendingCounterTracked = Boolean.TRUE.equals(user.getAccountPurgePendingCounterTracked());
            usersRepository.delete(user);
            if (pendingCounterTracked && notificationRuleRegistryService != null) {
                notificationRuleRegistryService.adjustPendingCount(
                        NotificationJobs.RULE_ACCOUNT_PURGE,
                        -1,
                        "admin.jobs.table.affected");
            }

            completeJob(job, Instant.now());
            return true;
        } catch (RuntimeException exception) {
            failJob(job, exception, Instant.now());
            return false;
        }
    }

    private AccountPurgeJob loadOrCreatePurgeJob(String userId, Instant now) {
        java.util.Optional<AccountPurgeJob> existing = accountPurgeJobsRepository.findByUserId(userId);
        if (existing != null && existing.isPresent()) {
            return existing.get();
        }
        AccountPurgeJob job = new AccountPurgeJob();
        String nowIso = now.toString();
        job.setUserId(userId);
        job.setBaseGroupId(JobGroupContext.groupId());
        job.setUserHash(hashUserId(userId));
        job.setStatus(AccountPurgeJob.STATUS_PENDING);
        job.setStage("queued");
        job.setRetryCount(0);
        job.setCreatedDate(nowIso);
        job.setUpdatedDate(nowIso);
        AccountPurgeJob saved = accountPurgeJobsRepository.save(job);
        return saved == null ? job : saved;
    }

    private boolean shouldRunPurgeJob(AccountPurgeJob job, Instant now) {
        if (job == null || AccountPurgeJob.STATUS_COMPLETED.equals(safeText(job.getStatus()))) {
            return false;
        }
        if (AccountPurgeJob.STATUS_RUNNING.equals(safeText(job.getStatus())) && isFuture(job.getLockedUntilIso(), now)) {
            return false;
        }
        return !AccountPurgeJob.STATUS_FAILED.equals(safeText(job.getStatus())) || !isFuture(job.getNextRetryAtIso(), now);
    }

    private void startJob(AccountPurgeJob job, String userId, Instant now) {
        String nowIso = now.toString();
        job.setUserId(userId);
        job.setBaseGroupId(JobGroupContext.groupId());
        job.setUserHash(hashUserId(userId));
        job.setStatus(AccountPurgeJob.STATUS_RUNNING);
        job.setStage("start");
        job.setNextRetryAtIso(null);
        job.setLockedUntilIso(now.plus(PURGE_JOB_LEASE).toString());
        if (!StringUtils.hasText(safeText(job.getStartedAtIso()))) {
            job.setStartedAtIso(nowIso);
        }
        job.setLastError(null);
        job.setUpdatedDate(nowIso);
        saveJob(job);
    }

    private void markStage(AccountPurgeJob job, String stage) {
        Instant now = Instant.now();
        job.setStatus(AccountPurgeJob.STATUS_RUNNING);
        job.setStage(stage);
        job.setLockedUntilIso(now.plus(PURGE_JOB_LEASE).toString());
        job.setUpdatedDate(now.toString());
        saveJob(job);
    }

    private void completeJob(AccountPurgeJob job, Instant now) {
        job.setStatus(AccountPurgeJob.STATUS_COMPLETED);
        job.setStage("completed");
        job.setUserId(null);
        job.setOwnedEventIds(List.of());
        job.setOwnedAssetIds(List.of());
        job.setNextRetryAtIso(null);
        job.setLockedUntilIso(null);
        job.setCompletedAtIso(now.toString());
        job.setLastError(null);
        job.setUpdatedDate(now.toString());
        saveJob(job);
    }

    private void failJob(AccountPurgeJob job, RuntimeException exception, Instant now) {
        int retryCount = Math.max(0, job.getRetryCount() == null ? 0 : job.getRetryCount().intValue()) + 1;
        job.setStatus(AccountPurgeJob.STATUS_FAILED);
        job.setRetryCount(retryCount);
        job.setNextRetryAtIso(now.plus(retryDelay(retryCount)).toString());
        job.setLockedUntilIso(null);
        job.setLastError(truncateError(exception));
        job.setUpdatedDate(now.toString());
        saveJob(job);
    }

    private AccountPurgeJob saveJob(AccountPurgeJob job) {
        AccountPurgeJob saved = accountPurgeJobsRepository.save(job);
        return saved == null ? job : saved;
    }

    private Duration retryDelay(int retryCount) {
        long seconds = PURGE_RETRY_BASE.toSeconds();
        for (int index = 1; index < Math.min(retryCount, 8); index += 1) {
            seconds = Math.min(PURGE_RETRY_MAX.toSeconds(), seconds * 2L);
        }
        return Duration.ofSeconds(Math.min(PURGE_RETRY_MAX.toSeconds(), seconds));
    }

    private boolean isFuture(String iso, Instant now) {
        if (!StringUtils.hasText(safeText(iso))) {
            return false;
        }
        try {
            return Instant.parse(iso).isAfter(now);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String truncateError(RuntimeException exception) {
        String value = exception == null ? "" : safeText(exception.getMessage());
        if (!StringUtils.hasText(value) && exception != null) {
            value = exception.getClass().getSimpleName();
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String hashUserId(String userId) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(("account-purge:" + safeText(userId)).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void purgeUserMedia(Users user) {
        if (user == null) {
            return;
        }
        for (String imageUrl : user.getImages() == null ? List.<String>of() : user.getImages()) {
            deletePurgeMedia(imageUrl);
        }
    }

    private void requireRecordedCleanupOwners(AccountPurgeJob job) {
        if (!job.getOwnedEventIds().isEmpty() || !job.getOwnedAssetIds().isEmpty()) {
            throw new IllegalStateException("Recorded product cleanup requires its installed owner.");
        }
    }

    private void deletePurgeMedia(String mediaUrlOrKey) {
        mediaStorageService.deletePublicObjectOrThrow(mediaUrlOrKey);
    }

    private void purgeUserReferencedDocuments(String userId) {
        deleteDocumentsByAnyField("userFriendships", List.of("leftUserId", "rightUserId"), userId);
        integrationPrivacy.purgeParticipantData(userId);
        deleteDocumentsByAnyField("privacyConsents", List.of("userId"), userId);
        deleteDocumentsByAnyField("accountDeletionWarnings", List.of("userId"), userId);
        deleteDocumentsByAnyField("userSessionActions", List.of("userId", "createdUser", "updatedUser"), userId);
        notificationPrivacy.purgeDeliveryData(userId);
        helpPrivacy.anonymizeAuthors(userId);
        notificationPrivacy.anonymizeRuleAuthors(userId);
    }

    private void deleteDocumentsByAnyField(String collectionName, List<String> fields, String userId) {
        if (!StringUtils.hasText(collectionName) || fields == null || fields.isEmpty() || !StringUtils.hasText(userId)) {
            return;
        }
        List<Criteria> criteria = fields.stream()
                .filter(StringUtils::hasText)
                .map(field -> Criteria.where(field).is(userId))
                .toList();
        if (criteria.isEmpty()) {
            return;
        }
        mongoTemplate.remove(Query.query(new Criteria().orOperator(criteria.toArray(Criteria[]::new))), collectionName);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
