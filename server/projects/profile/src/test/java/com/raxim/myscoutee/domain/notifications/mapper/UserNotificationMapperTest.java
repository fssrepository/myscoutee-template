package com.raxim.myscoutee.domain.notifications.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.raxim.myscoutee.domain.notifications.document.UserNotification;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;

@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Tag("standard")
class UserNotificationMapperTest {
    @Mock
    private AccountNotifications usersRepository;

    @Test
    void mapsPageContainingOnlySenderlessSystemNotification() {
        UserNotification notification = new UserNotification();
        notification.setId("notification-1");
        notification.setRecipientUserId("viewer-1");
        notification.setKind("system-update");
        notification.setCategory("user");
        notification.setTitle("System update");
        notification.setMessage("A sender is intentionally absent.");
        notification.setSenderUserId(null);
        notification.setActionPath("/game");
        notification.setSourceType("system");
        notification.setSourceId("system-1");
        notification.setPayload(Map.of("type", "system-update"));
        notification.setCreatedDate("2026-07-30T00:00:00Z");

        var records = new UserNotificationMapper(usersRepository)
                .toDtos(List.of(notification));

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().id()).isEqualTo("notification-1");
        assertThat(records.getFirst().senderUserId()).isNull();
        assertThat(records.getFirst().senderName()).isNull();
        assertThat(records.getFirst().payload())
                .containsEntry("type", "system-update");
        verifyNoInteractions(usersRepository);
    }
}
