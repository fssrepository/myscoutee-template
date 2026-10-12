package com.raxim.myscoutee.domain.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem;
import com.raxim.myscoutee.domain.notifications.document.UserNotification;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationDto;
import com.raxim.myscoutee.domain.notifications.repository.UserNotificationPageRepository;
import com.raxim.myscoutee.domain.notifications.repository.UserNotificationPageRepository.UserNotificationSlice;
import com.raxim.myscoutee.domain.notifications.repository.UserNotificationPageRepository.AggregatedNotificationUpsert;
import com.raxim.myscoutee.domain.notifications.repository.UserNotificationsRepository;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.notifications.mapper.UserNotificationMapper;

@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Tag("standard")
class UserNotificationServiceTest {
    @Mock
    private UserNotificationsRepository userNotificationsRepository;
    @Mock
    private UserNotificationPageRepository userNotificationPageRepository;
    @Mock
    private AccountNotifications accounts;
    @Mock
    private UserNotificationMapper userNotificationMapper;

    private UserNotificationService service;

    @BeforeEach
    void setUp() {
        service = new UserNotificationService(
                userNotificationsRepository,
                userNotificationPageRepository,
                accounts,
                userNotificationMapper);
    }

    @Test
    void materializeFansOutOneUnreadRecordPerRecipientAndIsSchedulerCategorized() {
        when(userNotificationPageRepository.insertIfAbsent(any(UserNotification.class))).thenReturn(true);
        NotificationOutboxItem outbox = outbox(
                "event-random-groups",
                List.of("viewer-1", "viewer-2", "viewer-1"));
        outbox.setRecipientScope("random-room-participants");

        service.materialize(outbox);

        ArgumentCaptor<UserNotification> notification = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationPageRepository, org.mockito.Mockito.times(2))
                .insertIfAbsent(notification.capture());
        assertThat(notification.getAllValues())
                .extracting(UserNotification::getRecipientUserId)
                .containsExactly("viewer-1", "viewer-2");
        assertThat(notification.getAllValues())
                .extracting(UserNotification::getCategory)
                .containsOnly("scheduled");
        assertThat(notification.getAllValues())
                .extracting(UserNotification::getSyncKey)
                .containsExactly("outbox-sync-1:viewer-1", "outbox-sync-1:viewer-2");
        verify(accounts).incrementUnreadCounter("viewer-1", 1);
        verify(accounts).incrementUnreadCounter("viewer-2", 1);
    }

    @Test
    void materializeCategorizesMemberApprovalAsEventAdmin() {
        when(userNotificationPageRepository.insertIfAbsent(any(UserNotification.class))).thenReturn(true);

        service.materialize(outbox("event-member-approved", List.of("member-1")));

        ArgumentCaptor<UserNotification> notification = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationPageRepository).insertIfAbsent(notification.capture());
        assertThat(notification.getValue().getCategory()).isEqualTo("event-admin");
        assertThat(notification.getValue().getReadAtIso()).isNull();
    }

    @Test
    void duplicateMaterializationDoesNotIncrementUnreadCounterAgain() {
        when(userNotificationPageRepository.insertIfAbsent(any(UserNotification.class))).thenReturn(false);

        service.materialize(outbox("event-invite", List.of("viewer-1")));

        verify(accounts, org.mockito.Mockito.never()).incrementUnreadCounter(any(String.class), eq(1));
    }

    @Test
    void materializeAggregatesMatchingWarningsIntoOneUnreadRow() {
        when(userNotificationPageRepository.upsertAggregated(any(UserNotification.class)))
                .thenReturn(new AggregatedNotificationUpsert(false, false));
        NotificationOutboxItem outbox = outbox("event-ticket-replay-warning", List.of("holder-1"));
        outbox.setPayload(Map.of(
                "notification_aggregation_key", "event-ticket-replay:ticket-1",
                "notification_tone", "warning",
                "actorUserId", "scanner-2"));

        service.materialize(outbox);

        ArgumentCaptor<UserNotification> notification = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationPageRepository).upsertAggregated(notification.capture());
        assertThat(notification.getValue().getAggregationKey())
                .isEqualTo("holder-1:event-ticket-replay:ticket-1");
        assertThat(notification.getValue().getOccurrenceCount()).isEqualTo(1);
        assertThat(notification.getValue().getSenderUserId()).isEqualTo("scanner-2");
        verify(userNotificationPageRepository, org.mockito.Mockito.never())
                .insertIfAbsent(any(UserNotification.class));
        verify(accounts, org.mockito.Mockito.never()).incrementUnreadCounter(any(String.class), eq(1));
    }

    @Test
    void newAggregatedWarningIncrementsUnreadRowsOnlyOnce() {
        when(userNotificationPageRepository.upsertAggregated(any(UserNotification.class)))
                .thenReturn(new AggregatedNotificationUpsert(true, true));
        NotificationOutboxItem outbox = outbox("event-ticket-replay-warning", List.of("holder-1"));
        outbox.setPayload(Map.of(
                "notification_aggregation_key", "event-ticket-replay:ticket-1"));

        service.materialize(outbox);

        verify(accounts).incrementUnreadCounter("holder-1", 1);
    }

    @Test
    void materializeAlwaysProvidesRenderableTitleAndMessageText() {
        when(userNotificationPageRepository.insertIfAbsent(any(UserNotification.class))).thenReturn(true);
        NotificationOutboxItem outbox = outbox("event-invite", List.of("viewer-1"));
        outbox.setTitle(null);
        outbox.setBody(null);
        outbox.setPayload(Map.of());

        service.materialize(outbox);

        ArgumentCaptor<UserNotification> notification = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationPageRepository).insertIfAbsent(notification.capture());
        assertThat(notification.getValue().getTitle()).isEqualTo("Notification");
        assertThat(notification.getValue().getMessage()).isEqualTo("You have a new update.");
    }

    @Test
    void materializeNormalizesAbsoluteClickUrlToInternalActionPath() {
        when(userNotificationPageRepository.insertIfAbsent(any(UserNotification.class))).thenReturn(true);
        NotificationOutboxItem outbox = outbox("scheduled-template", List.of("viewer-1"));
        outbox.setClickUrl("https://example.test/game?chatId=x");

        service.materialize(outbox);

        ArgumentCaptor<UserNotification> notification = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationPageRepository).insertIfAbsent(notification.capture());
        assertThat(notification.getValue().getActionPath())
                .isEqualTo("/game?chatId=x");
    }

    @Test
    void pageCursorIsOpaqueAndCannotBeReusedAcrossBuckets() {
        UserNotification record = new UserNotification();
        record.setId("notification-1");
        record.setCreatedDate("2026-07-27T19:00:00Z");
        when(userNotificationPageRepository.findPage(eq("viewer-1"), eq(true), eq(1), any(), isNull()))
                .thenReturn(new UserNotificationSlice(List.of(record), 2L, true));
        when(userNotificationMapper.toDtos(List.of(record))).thenReturn(List.of(dto(record)));
        when(userNotificationPageRepository.countUnread("viewer-1")).thenReturn(2L);

        var firstPage = service.queryPage("viewer-1", "new", 1, null);

        assertThat(firstPage.nextCursor()).isNotBlank().doesNotContain("2026-07-27");
        assertThatThrownBy(() -> service.queryPage("viewer-1", "all", 1, firstPage.nextCursor()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void markReadIsIdempotentAndReturnsAuthoritativeUnreadCount() {
        UserNotification alreadyRead = new UserNotification();
        alreadyRead.setId("notification-1");
        alreadyRead.setRecipientUserId("viewer-1");
        alreadyRead.setReadAtIso("2026-07-27T19:00:00Z");
        when(userNotificationPageRepository.markRead(
                eq("notification-1"),
                eq("viewer-1"),
                any(String.class))).thenReturn(Optional.empty());
        when(userNotificationsRepository.findByIdAndRecipientUserId("notification-1", "viewer-1"))
                .thenReturn(Optional.of(alreadyRead));
        when(userNotificationPageRepository.countUnread("viewer-1")).thenReturn(3L);
        when(userNotificationMapper.toDto(alreadyRead)).thenReturn(dto(alreadyRead));

        var response = service.markRead("viewer-1", "notification-1");

        assertThat(response.unreadCount()).isEqualTo(3);
        assertThat(response.notification().id()).isEqualTo("notification-1");
        verify(accounts, org.mockito.Mockito.never()).incrementUnreadCounter("viewer-1", -1);
    }

    @Test
    void firstReadAtomicallyDecrementsTheStoredUnreadCounterOnce() {
        UserNotification markedRead = new UserNotification();
        markedRead.setId("notification-1");
        markedRead.setRecipientUserId("viewer-1");
        markedRead.setReadAtIso("2026-07-27T19:00:00Z");
        when(userNotificationPageRepository.markRead(
                eq("notification-1"),
                eq("viewer-1"),
                any(String.class))).thenReturn(Optional.of(markedRead));
        when(userNotificationPageRepository.countUnread("viewer-1")).thenReturn(2L);
        when(userNotificationMapper.toDto(markedRead)).thenReturn(dto(markedRead));

        var response = service.markRead("viewer-1", "notification-1");

        assertThat(response.unreadCount()).isEqualTo(2);
        verify(accounts).incrementUnreadCounter("viewer-1", -1);
    }

    @Test
    void readingChatMessagesReadsOnlyTheirNotificationsAndDecrementsOnce() {
        when(accounts.notificationAccountId("viewer-1")).thenReturn("viewer-1");
        when(userNotificationPageRepository.markUnreadChatMessagesRead(
                eq("viewer-1"),
                eq("chat-1"),
                eq(List.of("message-1", "message-2")),
                any(String.class))).thenReturn(2L);

        int changed = service.markChatMessagesRead(
                " viewer-1 ",
                " chat-1 ",
                List.of("message-1", "message-2"));

        assertThat(changed).isEqualTo(2);
        verify(accounts).incrementUnreadCounter("viewer-1", -2);
    }

    @Test
    void readingAWholeChatResolvesEveryUnreadNotificationFromThatChat() {
        when(accounts.notificationAccountId("viewer-1")).thenReturn("viewer-1");
        when(userNotificationPageRepository.markUnreadBySource(
                eq("viewer-1"),
                eq("chat-message"),
                eq("chat"),
                eq("chat-1"),
                any(String.class))).thenReturn(3L);

        int changed = service.markChatRead(" viewer-1 ", " chat-1 ");

        assertThat(changed).isEqualTo(3);
        verify(accounts).incrementUnreadCounter("viewer-1", -3);
    }

    @Test
    void pageReturnsStoredMutedPreferenceAndPreferenceMutationUsesUserScope() {
        when(accounts.muted("viewer-1")).thenReturn(true);
        when(userNotificationPageRepository.findPage("viewer-1", true, 20, null, null))
                .thenReturn(new UserNotificationSlice(List.of(), 0L, false));
        when(userNotificationMapper.toDtos(List.of())).thenReturn(List.of());
        when(userNotificationPageRepository.countUnread("viewer-1")).thenReturn(0L);

        var page = service.queryPage("viewer-1", "new", null, null);
        var preference = service.updateMutedPreference("viewer-1", false);

        assertThat(page.muted()).isTrue();
        assertThat(preference.muted()).isFalse();
        verify(accounts).setMuted("viewer-1", false);
    }

    private NotificationOutboxItem outbox(String kind, List<String> recipients) {
        NotificationOutboxItem outbox = new NotificationOutboxItem();
        outbox.setId("outbox-1");
        outbox.setSyncKey("outbox-sync-1");
        outbox.setNotificationType(kind);
        outbox.setEntityType("event");
        outbox.setEntityId("event-1");
        outbox.setRecipientUserIds(recipients);
        outbox.setTitle("Rooms are ready");
        outbox.setBody("Your random room assignment is ready.");
        outbox.setClickUrl("/activities/events/event-1");
        outbox.setPayload(Map.of("eventId", "event-1"));
        outbox.setCreatedDate("2026-07-27T19:00:00Z");
        outbox.setCreatedUser("scheduler");
        return outbox;
    }

    private UserNotificationDto dto(UserNotification notification) {
        return new UserNotificationDto(
                notification.getId(),
                notification.getRecipientUserId(),
                notification.getKind(),
                notification.getCategory(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedDate(),
                notification.getReadAtIso(),
                notification.getSenderUserId(),
                null,
                null,
                notification.getActionPath(),
                notification.getSourceType(),
                notification.getSourceId(),
                Map.of(),
                1,
                1L);
    }
}
