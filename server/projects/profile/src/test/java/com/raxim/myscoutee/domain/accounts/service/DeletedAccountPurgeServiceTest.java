package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;
import com.raxim.myscoutee.domain.accounts.document.*;
import com.raxim.myscoutee.domain.accounts.repository.*;
import com.raxim.myscoutee.domain.chat.api.ChatPrivacy;
import com.raxim.myscoutee.domain.help.service.HelpPrivacyService;
import com.raxim.myscoutee.domain.media.api.MediaCleanup;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.notifications.service.NotificationPrivacyService;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.domain.platform.service.JobGroupScopeService;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;

@Tag("standard")
class DeletedAccountPurgeServiceTest {
    private final UsersRepository users = mock(UsersRepository.class);
    private final AccountPurgeJobsRepository jobs = mock(AccountPurgeJobsRepository.class);
    private final MediaCleanup media = mock(MediaCleanup.class);
    private final ChatPrivacy chat = mock(ChatPrivacy.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final NotificationJobs scheduling = mock(NotificationJobs.class);
    private DeletedAccountPurgeService service;
    private Users user;
    private AccountPurgeJob job;

    @BeforeEach void setup() {
        user = new Users(); user.setId("expired-member"); user.setUserId("expired-member");
        user.setImages(List.of("avatar.webp")); user.setAccountPurgePendingCounterTracked(true);
        job = new AccountPurgeJob(); job.setId("purge"); job.setUserId(user.getId());
        job.setStatus(AccountPurgeJob.STATUS_PENDING);
        when(users.findByProfileStatusAndDeletedAtIsoLessThanEqual(eq("deleted"), anyString()))
                .thenReturn(List.of(user));
        when(jobs.findByUserId(user.getId())).thenReturn(Optional.of(job));
        when(jobs.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new DeletedAccountPurgeService(users, chat, jobs, media, mongo, null);
        service.setNotificationRuleRegistryService(scheduling);
        doAnswer(call -> { JobGroupContext.run(null, call.getArgument(1, Runnable.class)); return null; })
                .when(scheduling).forEachGroup(eq(NotificationJobs.RULE_ACCOUNT_PURGE), any(Runnable.class));
        ReflectionTestUtils.setField(service, "jobGroupScope", new JobGroupScopeService(
                mock(ProfileDirectory.class), Optional.empty(), List.of()));
        ReflectionTestUtils.setField(service, "helpPrivacy", new HelpPrivacyService(mongo));
        ReflectionTestUtils.setField(service, "notificationPrivacy", new NotificationPrivacyService(mongo));
        ReflectionTestUtils.setField(service, "integrationPrivacy",
                new com.raxim.myscoutee.domain.integrations.service.IntegrationPrivacyService(mongo));
    }

    @Test void retainedCleanupFinishesBeforeDeletingIdentityAndClearsJobIdentifiers() {
        service.purgeExpiredDeletedAccounts();
        var order = inOrder(media, chat, mongo, users);
        order.verify(media).deletePublicObjectOrThrow("avatar.webp");
        order.verify(chat).purgeUserChatActivity(user.getId());
        order.verify(mongo).remove(any(Query.class), eq("integrationTokens"));
        order.verify(mongo).remove(any(Query.class), eq("mcpAuthorizations"));
        order.verify(mongo).remove(any(Query.class), eq("privacyConsents"));
        order.verify(mongo).remove(any(Query.class), eq("userNotifications"));
        order.verify(users).delete(user);
        assertThat(job.getStatus()).isEqualTo(AccountPurgeJob.STATUS_COMPLETED);
        assertThat(job.getUserId()).isNull(); assertThat(job.getCompletedAtIso()).isNotBlank();
        assertThat(job.getLockedUntilIso()).isNull(); assertThat(job.getNextRetryAtIso()).isNull();
        verify(scheduling).adjustPendingCount(NotificationJobs.RULE_ACCOUNT_PURGE, -1, "admin.jobs.table.affected");
    }

    @Test void failedMediaLeavesIdentityAndDurableRetryThenCompletesAfterRetry() {
        doThrow(new IllegalStateException("object store unavailable")).doNothing()
                .when(media).deletePublicObjectOrThrow("avatar.webp");
        service.purgeExpiredDeletedAccounts();
        assertThat(job.getStatus()).isEqualTo(AccountPurgeJob.STATUS_FAILED);
        assertThat(job.getStage()).isEqualTo("profile-media");
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(Instant.parse(job.getNextRetryAtIso())).isAfter(Instant.now());
        verify(users, never()).delete(any()); verifyNoInteractions(chat);
        verify(scheduling, never()).adjustPendingCount(anyString(), anyInt(), anyString());
        service.purgeExpiredDeletedAccounts();
        verify(media).deletePublicObjectOrThrow("avatar.webp");
        job.setNextRetryAtIso(Instant.now().minusSeconds(1).toString());
        service.purgeExpiredDeletedAccounts();
        verify(media, times(2)).deletePublicObjectOrThrow("avatar.webp");
        verify(users).delete(user);
        assertThat(job.getStatus()).isEqualTo(AccountPurgeJob.STATUS_COMPLETED);
    }

    @Test void runningLeasePreventsDuplicateCleanup() {
        job.setStatus(AccountPurgeJob.STATUS_RUNNING);
        job.setLockedUntilIso(Instant.now().plusSeconds(60).toString());
        service.purgeExpiredDeletedAccounts();
        verifyNoInteractions(media, chat, mongo); verify(users, never()).delete(any());
    }

    @Test void completedJobCannotRunAgain() {
        job.setStatus(AccountPurgeJob.STATUS_COMPLETED);
        service.purgeExpiredDeletedAccounts();
        verifyNoInteractions(media, chat, mongo); verify(users, never()).delete(any());
    }

    @Test void retryResumesFromStoredIntentEvenWhenExpiryScanIsEmpty() {
        when(users.findByProfileStatusAndDeletedAtIsoLessThanEqual(anyString(), anyString())).thenReturn(List.of());
        when(jobs.findByStatusIn(any())).thenReturn(List.of(job));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        service.purgeExpiredDeletedAccounts();
        verify(users).delete(user);
        assertThat(job.getStatus()).isEqualTo(AccountPurgeJob.STATUS_COMPLETED);
    }

    @Test void aUserAlreadyDeletedOnAPreviousAttemptCompletesTheStoredIntent() {
        when(users.findByProfileStatusAndDeletedAtIsoLessThanEqual(anyString(), anyString())).thenReturn(List.of());
        when(jobs.findByStatusIn(any())).thenReturn(List.of(job));
        service.purgeExpiredDeletedAccounts();
        assertThat(job.getStatus()).isEqualTo(AccountPurgeJob.STATUS_COMPLETED);
        assertThat(job.getUserId()).isNull(); verifyNoInteractions(media, chat, mongo);
    }

    @Test void expiredOnboardingUsesTheSameCleanupAndNativeThirtyDayCutoff() {
        when(users.findByProfileStatusAndDeletedAtIsoLessThanEqual(eq("deleted"), anyString())).thenReturn(List.of());
        when(users.findByProfileStatusAndDeletedAtIsoLessThanEqual(eq("onboarding"), anyString())).thenReturn(List.of(user));
        service.purgeExpiredDeletedAccounts();
        verify(users).delete(user);
        verify(users).findByProfileStatusAndDeletedAtIsoLessThanEqual(eq("onboarding"), argThat(value ->
                Instant.parse(value).isAfter(Instant.now().minusSeconds(31 * 86400L))
                && Instant.parse(value).isBefore(Instant.now().minusSeconds(29 * 86400L))));
    }

    @Test void importedUnfinishedProductCleanupCannotBeDiscarded() {
        job.setOwnedEventIds(List.of("unfinished-event"));
        service.purgeExpiredDeletedAccounts();
        assertThat(job.getStatus()).isEqualTo(AccountPurgeJob.STATUS_FAILED);
        assertThat(job.getOwnedEventIds()).containsExactly("unfinished-event");
        verifyNoInteractions(media, chat, mongo); verify(users, never()).delete(any());
    }

    @Test void absentScheduleDoesNoAccountScan() {
        reset(scheduling);
        service.purgeExpiredDeletedAccounts();
        verifyNoInteractions(users, jobs, media, chat, mongo);
    }
}
