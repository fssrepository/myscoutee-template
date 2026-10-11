package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

@Tag("standard")
class AccountNotificationServiceTest {
    private final UsersRepository users = mock(UsersRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final AccountNotificationService service = new AccountNotificationService(users, mongo);

    @Test
    void recipientBatchReturnsDetachedValuesAndRetainsWorkspaceIdentity() {
        Users user = user("workspace-profile", device("phone", "token-before"));
        user.setAccountUserId("account");
        user.setWorkspaceGroupId("workspace");
        when(users.findByUserIdIn(List.of("workspace-profile"))).thenReturn(List.of(user));

        var result = service.recipients(List.of("workspace-profile")).getFirst();
        user.getDevices().getFirst().setFirebaseToken("token-after");
        user.getDevices().clear();

        assertThat(result.accountUserId()).isEqualTo("account");
        assertThat(result.workspaceGroupId()).isEqualTo("workspace");
        assertThat(result.devices().getFirst().token()).isEqualTo("token-before");
        assertThatThrownBy(() -> result.devices().clear()).isInstanceOf(UnsupportedOperationException.class);
        verify(users).findByUserIdIn(List.of("workspace-profile"));
        verifyNoMoreInteractions(users);
    }

    @Test
    void emptyBatchesAndZeroCounterChangesDoNotTouchPersistence() {
        assertThat(service.recipients(List.of())).isEmpty();
        assertThat(service.senders(List.of())).isEmpty();
        assertThat(service.roleRecipients(List.of())).isEmpty();
        assertThat(service.removeInvalidPushTokens(List.of("account"), Set.of()).removedTokens()).isZero();
        service.incrementUnreadCounter("account", 0);
        verifyNoInteractions(users, mongo);
    }

    @Test
    void tokenRotationBeforeCleanupIsNotOverwrittenByTheDeliverySnapshot() {
        Users current = user("account", device("phone", "rotated-token"));
        when(users.findByUserIdIn(List.of("account"))).thenReturn(List.of(current));

        var result = service.removeInvalidPushTokens(List.of("account"), Set.of("expired-token"));

        assertThat(result.removedTokens()).isZero();
        assertThat(current.getDevices().getFirst().getFirebaseToken()).isEqualTo("rotated-token");
        verify(users, never()).saveAll(any());
        verify(users).findByUserIdIn(List.of("account"));
        verifyNoMoreInteractions(users);
    }

    @Test
    void cleanupRemovesOnlyRejectedTokensFromTheRequestedAccountsInOneBatch() {
        Users current = user("account", device("old", " expired-token "), device("current", "current-token"));
        current.setName("Updated profile name");
        when(users.findByUserIdIn(List.of("account"))).thenReturn(List.of(current));

        var result = service.removeInvalidPushTokens(List.of("account"), Set.of("expired-token"));

        assertThat(result.removedTokens()).isEqualTo(1);
        assertThat(result.changedUsers()).isEqualTo(1);
        assertThat(current.getDevices()).extracting(Users.UserDeviceRecord::getFirebaseToken).containsExactly("current-token");
        assertThat(current.getName()).isEqualTo("Updated profile name");
        verify(users).findByUserIdIn(List.of("account"));
        verify(users).saveAll(List.of(current));
        verifyNoMoreInteractions(users);
    }

    @Test
    void muteAndCounterCommandsPreserveTheirExistingTargetedMongoUpdates() {
        service.setMuted("account", true);
        service.incrementUnreadCounter("account", -2);
        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(query.capture(), update.capture(), eq(Users.class));
        assertThat(query.getAllValues()).allSatisfy(q -> assertThat(q.getQueryObject()).containsEntry("userId", "account"));
        assertThat(update.getAllValues().get(0).getUpdateObject().get("$set", org.bson.Document.class))
                .containsEntry("notificationPreferences.muted", true);
        assertThat(update.getAllValues().get(1).getUpdateObject().get("$inc", org.bson.Document.class))
                .containsEntry("activities.notifications", -2);
        verifyNoInteractions(users);
    }

    @Test
    void inboxIdentityAndMuteDefaultRemainAccountOwned() {
        Users profile = user("workspace");
        profile.setAccountUserId("account");
        when(users.findByUserId("workspace")).thenReturn(Optional.of(profile));
        when(users.findByUserId("missing")).thenReturn(Optional.empty());
        assertThat(service.notificationAccountId("workspace")).isEqualTo("account");
        assertThat(service.notificationAccountId("missing")).isEqualTo("missing");
        assertThat(service.muted("missing")).isFalse();
    }

    private Users user(String id, Users.UserDeviceRecord... devices) {
        Users user = new Users();
        user.setUserId(id);
        user.setDevices(new ArrayList<>(List.of(devices)));
        return user;
    }

    private Users.UserDeviceRecord device(String id, String token) {
        var device = new Users.UserDeviceRecord();
        device.setDeviceId(id);
        device.setFirebaseToken(token);
        device.setNotificationsEnabled(true);
        return device;
    }
}
