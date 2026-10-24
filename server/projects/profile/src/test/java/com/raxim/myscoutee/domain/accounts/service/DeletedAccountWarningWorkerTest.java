package com.raxim.myscoutee.domain.accounts.service;

import com.raxim.myscoutee.domain.email.service.EmailTemplateRenderer;

import com.raxim.myscoutee.domain.email.api.EmailSendResult;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.localization.dto.I18nBundleResponseDto;
import com.raxim.myscoutee.domain.localization.service.I18nService;
import com.raxim.myscoutee.common.config.PlatformParametersService;
import com.raxim.myscoutee.domain.notifications.service.NotificationRuleRegistryService;
import com.raxim.myscoutee.domain.email.service.OperatorEmailConfigurationService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@org.junit.jupiter.api.Tag("standard")
class DeletedAccountWarningWorkerTest {
    final MongoTemplate mongo = mock(MongoTemplate.class);
    final PlatformParametersService parameters = mock(PlatformParametersService.class);
    final OperatorEmailConfigurationService email = mock(OperatorEmailConfigurationService.class);
    final I18nService i18n = mock(I18nService.class);
    final DeletedAccountWarningWorker worker = new DeletedAccountWarningWorker(mongo, parameters, email,
            mock(NotificationRuleRegistryService.class), mock(ScheduledMongoSessionRunner.class), i18n, new EmailTemplateRenderer(), null);
    final Instant now = Instant.parse("2026-09-22T12:00:00Z");
    final Users user = new Users();
    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(worker, "jobGroupScope", new AccountJobQueries(new com.raxim.myscoutee.domain.platform.service.JobGroupScopeService(
                mock(com.raxim.myscoutee.domain.accounts.api.ProfileDirectory.class), java.util.Optional.empty(), List.of())));
        user.setUserId("deleted-user"); user.setName("Viewer"); user.setEmail("viewer@example.test");
        user.setProfileStatus("deleted"); user.setDeletedAtIso("2026-08-28T12:00:00Z");
        when(email.enabled()).thenReturn(true); when(parameters.accountPurgeWindowDays(30)).thenReturn(30);
        when(i18n.getBundle("en", "en", null)).thenReturn(new I18nBundleResponseDto("en", "1",
                Map.of("account.deletion.warning.subject", "Deletion warning", "account.deletion.warning.body", "Hello {{name}}, deletion on {{purgeAt}}.")));
        when(mongo.find(any(Query.class), eq(Users.class))).thenReturn(List.of(user));
        when(mongo.findOne(any(Query.class), eq(Users.class))).thenReturn(user);
        when(email.send(any())).thenReturn(new EmailSendResult(true, "stub-message", null));
    }
    @Test void selectsOnlyDeletedAccountsWithinFixedFiveDayWindowAndSendsOneReminder() {
        assertEquals(1, worker.deliver(now));
        var query = ArgumentCaptor.forClass(Query.class); verify(mongo).find(query.capture(), eq(Users.class));
        var filter = query.getValue().getQueryObject(); assertEquals("deleted", filter.getString("profileStatus"));
        var dates = (Document) filter.get("deletedAtIso");
        assertEquals("2026-08-23T12:00:00Z", dates.getString("$gt")); assertEquals("2026-08-28T12:00:00Z", dates.getString("$lte"));
        assertEquals(100, query.getValue().getLimit());
        var message = ArgumentCaptor.forClass(EmailSendRequest.class); verify(email).send(message.capture());
        assertEquals("viewer@example.test", message.getValue().toEmail());
        assertTrue(message.getValue().textBody().contains("2026-09-27T12:00:00Z"));
    }
    @Test void repeatedJobDoesNotSendAnAlreadyClaimedEpisode() {
        when(mongo.findById(anyString(), eq(Document.class), eq("accountDeletionWarnings"))).thenReturn(new Document("status", "sent"));
        assertEquals(0, worker.deliver(now)); verify(email, never()).send(any());
    }
    @Test void restoredAccountIsRecheckedBeforeSending() {
        when(mongo.findOne(any(Query.class), eq(Users.class))).thenReturn(null);
        assertEquals(0, worker.deliver(now)); verify(email, never()).send(any());
    }
    @Test void disabledProviderLeavesCandidatesUntouched() {
        when(email.enabled()).thenReturn(false); assertEquals(0, worker.deliver(now)); verifyNoInteractions(mongo);
    }
    @Test void unsubscribeIsTerminalWithoutCountingDeliveryOrFailure() {
        when(email.send(any())).thenReturn(EmailSendResult.unsubscribed());
        assertEquals(0, worker.deliver(now));
        var update = ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq("accountDeletionWarnings"));
        assertEquals("unsubscribed", ((Document) update.getValue().getUpdateObject().get("$set")).getString("status"));
        assertTrue(((Document) update.getValue().getUpdateObject().get("$unset")).containsKey("nextAttemptAtIso"));
    }
    @Test void failedDeliveryFailsJobRatherThanRecordingSuccess() {
        when(email.send(any())).thenReturn(new EmailSendResult(false, null, "unauthorized"));
        assertThrows(IllegalStateException.class, () -> worker.deliver(now));
        verify(email, times(1)).send(any());
    }
    @Test void unknownDeliveryIsPersistedAndNeverAutomaticallyResent() {
        when(email.send(any())).thenReturn(EmailSendResult.unknown("Response lost"));
        assertThrows(IllegalStateException.class, () -> worker.deliver(now));
        var update = ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq("accountDeletionWarnings"));
        assertEquals("unknown", ((Document) update.getValue().getUpdateObject().get("$set")).getString("status"));
        when(mongo.findById(anyString(), eq(Document.class), eq("accountDeletionWarnings")))
                .thenReturn(new Document("status", "unknown"));
        assertEquals(0, worker.deliver(now.plusSeconds(60)));
        verify(email, times(1)).send(any());
    }
    @Test void transientFailureSchedulesRetryAndMarksEpisodeClaimed() {
        Instant current = Instant.now();
        user.setDeletedAtIso(current.minus(25, java.time.temporal.ChronoUnit.DAYS).toString());
        when(email.send(any())).thenReturn(EmailSendResult.retryable("unavailable", 90));
        assertThrows(IllegalStateException.class, () -> worker.deliver(current));
        var update = ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq("accountDeletionWarnings"));
        Document fields = (Document) update.getValue().getUpdateObject().get("$set");
        assertEquals("retry", fields.getString("status"));
        assertTrue(Instant.parse(fields.getString("nextAttemptAtIso")).isAfter(current.plusSeconds(89)));
        verify(mongo).updateFirst(any(Query.class), any(org.springframework.data.mongodb.core.query.Update.class), eq(Users.class));
    }
    @Test void threeRetriesIncreaseDelayThenStopPermanently() {
        Instant current = Instant.now();
        user.setDeletedAtIso(current.minus(25, java.time.temporal.ChronoUnit.DAYS).toString());
        when(mongo.find(any(Query.class), eq(Users.class))).thenReturn(List.of());
        when(email.send(any())).thenReturn(EmailSendResult.retryable("unavailable", 0));
        for (int attempt = 2; attempt <= 4; attempt++) {
            var retry = new Document("_id", "episode").append("userId", user.getUserId())
                    .append("deletedAtIso", user.getDeletedAtIso()).append("attemptCount", attempt - 1);
            var claim = new Document(retry).append("attemptCount", attempt);
            when(mongo.find(any(Query.class), eq(Document.class), eq("accountDeletionWarnings"))).thenReturn(List.of(retry));
            when(mongo.findAndModify(any(Query.class), any(org.springframework.data.mongodb.core.query.Update.class),
                    any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Document.class), eq("accountDeletionWarnings")))
                    .thenReturn(claim);
            assertThrows(IllegalStateException.class, () -> worker.deliver(current));
            var update = ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
            verify(mongo).updateFirst(any(Query.class), update.capture(), eq("accountDeletionWarnings"));
            Document fields = (Document) update.getValue().getUpdateObject().get("$set");
            assertEquals(attempt < 4 ? "retry" : "failed", fields.getString("status"));
            if (attempt < 4) assertTrue(Instant.parse(fields.getString("nextAttemptAtIso"))
                    .isAfter(current.plusSeconds((60L << (attempt - 1)) - 1)));
            else assertTrue(((Document) update.getValue().getUpdateObject().get("$unset")).containsKey("nextAttemptAtIso"));
            clearInvocations(mongo);
        }
        verify(email, times(3)).send(any());
    }
    @Test void retryClaimLostToAnotherWorkerDoesNotSend() {
        when(mongo.find(any(Query.class), eq(Users.class))).thenReturn(List.of());
        when(mongo.find(any(Query.class), eq(Document.class), eq("accountDeletionWarnings")))
                .thenReturn(List.of(new Document("_id", "episode").append("attemptCount", 1)));
        assertEquals(0, worker.deliver(now));
        verify(email, never()).send(any());
    }
}
