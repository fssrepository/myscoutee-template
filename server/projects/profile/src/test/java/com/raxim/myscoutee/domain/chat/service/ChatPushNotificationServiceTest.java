package com.raxim.myscoutee.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.raxim.myscoutee.domain.chat.document.Chat;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.notifications.service.NotificationOutboxService;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.NotificationIntent;

@org.junit.jupiter.api.Tag("standard")
class ChatPushNotificationServiceTest {
    @Test
    void enqueueExcludesARecipientActivelyReadingTheChat() {
        UsersRepository usersRepository = org.mockito.Mockito.mock(UsersRepository.class);
        NotificationOutboxService notificationOutboxService = org.mockito.Mockito.mock(NotificationOutboxService.class);
        ChatWebSocketSessionRegistry sessionRegistry = org.mockito.Mockito.mock(ChatWebSocketSessionRegistry.class);
        when(usersRepository.findByUserId("sender")).thenReturn(Optional.empty());
        when(sessionRegistry.isUserConnected("chat-1", "connected-viewer")).thenReturn(true);
        ChatPushNotificationService service = new ChatPushNotificationService(
                new com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService(usersRepository),
                notificationOutboxService,
                sessionRegistry);
        Chat chat = new Chat();
        chat.setChatId("chat-1");
        chat.setOwnerUserId("connected-viewer");
        chat.setMemberIds(List.of("sender", "connected-viewer", "offline-viewer"));
        ChatStreamEvent event = ChatStreamEvent.message(
                "chat-1",
                "message-1",
                "client-1",
                "sender",
                "Sender",
                "S",
                "",
                "",
                "Hello",
                "2026-07-27T20:00:00Z",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of());

        service.notifyRecipients(List.of(chat), event);

        ArgumentCaptor<NotificationIntent> intent = ArgumentCaptor.forClass(NotificationIntent.class);
        verify(notificationOutboxService).enqueue(intent.capture());
        assertThat(intent.getValue().iconUrl()).isNull();
        assertThat(intent.getValue().badgeUrl()).isNull();
        assertThat(intent.getValue().recipientUserIds())
                .containsExactly("offline-viewer")
                .doesNotContain("connected-viewer")
                .doesNotContain("sender");
    }

    @Test
    void doesNotCreateNotificationsForSystemChatMessages() {
        UsersRepository usersRepository = org.mockito.Mockito.mock(UsersRepository.class);
        NotificationOutboxService notificationOutboxService = org.mockito.Mockito.mock(NotificationOutboxService.class);
        ChatWebSocketSessionRegistry sessionRegistry = org.mockito.Mockito.mock(ChatWebSocketSessionRegistry.class);
        ChatPushNotificationService service = new ChatPushNotificationService(
                new com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService(usersRepository),
                notificationOutboxService,
                sessionRegistry);
        Chat chat = new Chat();
        chat.setChatId("chat-1");
        chat.setOwnerUserId("admin");
        chat.setMemberIds(List.of("admin", "accepted-member"));
        ChatStreamEvent event = ChatStreamEvent.message(
                "chat-1",
                "message-1",
                "member-joined",
                "",
                "System",
                "MS",
                "system",
                "",
                "Accepted Member joined the event.",
                "2026-08-04T19:55:00Z",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of());

        service.notifyRecipients(List.of(chat), event);

        verifyNoInteractions(usersRepository, notificationOutboxService);
    }
}
