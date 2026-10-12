package com.raxim.myscoutee.domain.notifications.service;

import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import com.mongodb.client.result.UpdateResult;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.notifications.repository.NotificationOutboxRepository;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.accounts.service.AccountNotificationService;
import com.raxim.myscoutee.domain.administration.service.AdminTelemetryService;
import com.raxim.myscoutee.domain.chat.service.ChatWebSocketSessionRegistry;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery.FirebaseBatchSendResult;

class NotificationDeliveryDecisionTest {
    private final ConfigProperties config = new ConfigProperties();
    private final NotificationOutboxRepository outbox = mock(NotificationOutboxRepository.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final ChatWebSocketSessionRegistry sessions = mock(ChatWebSocketSessionRegistry.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final FirebasePushDelivery gateway = mock(FirebasePushDelivery.class);

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void workspaceProfilesResolveOnlyMissingBaseAccountsAndKeepTheirDeviceIdentity() {
        config.setFirebaseEnabled(true);
        Users first = user("profile-1", device("workspace-device", "not-an-account-token", true));
        first.setAccountUserId("account");
        first.setWorkspaceGroupId("workspace");
        Users second = user("profile-2");
        second.setAccountUserId("account");
        second.setWorkspaceGroupId("workspace");
        when(users.findByUserIdIn(List.of("profile-1", "profile-2"))).thenReturn(List.of(first, second));
        when(users.findByUserIdIn(List.of("account"))).thenReturn(List.of(user("account", device("phone", "account-token", true))));

        var item = producer().enqueue(intent(List.of("profile-1", "profile-2"))).orElseThrow();

        assertThat(item.getRecipientUserIds()).containsExactly("account");
        assertThat(item.getPayload()).containsEntry("workspaceGroupId", "workspace");
        assertThat(item.getFirebaseDevices()).containsOnlyKeys("account");
        assertThat(item.getFirebaseDevices().get("account"))
                .containsExactlyEntriesOf(Map.of("phone", NotificationOutboxService.sha256("account-token")));
        verify(users).findByUserIdIn(List.of("profile-1", "profile-2"));
        verify(users).findByUserIdIn(List.of("account"));
        verifyNoMoreInteractions(users);
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void mixedProfileAndBaseAccountAudienceReusesTheAlreadyLoadedAccount() {
        config.setFirebaseEnabled(true);
        Users profile = user("profile");
        profile.setAccountUserId("account");
        when(users.findByUserIdIn(List.of("profile", "account")))
                .thenReturn(List.of(profile, user("account", device("phone", "account-token", true))));

        var item = producer().enqueue(intent(List.of("profile", "account"))).orElseThrow();

        assertThat(item.getRecipientUserIds()).containsExactly("account");
        assertThat(item.getFirebaseDevices().get("account")).containsKey("phone");
        verify(users).findByUserIdIn(List.of("profile", "account"));
        verifyNoMoreInteractions(users);
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void enqueueSnapshotsOnlyEnabledDevicesButKeepsTheWholeInboxAudience() {
        config.setFirebaseEnabled(true);
        Users allowed = user("allowed", device("phone", "test-token", true));
        Users denied = user("denied", device("denied-phone", "denied-token", false));
        Users absent = user("absent");
        when(users.findByUserIdIn(anyList())).thenReturn(List.of(allowed, denied, absent));
        UserNotificationService inbox = mock(UserNotificationService.class);
        @SuppressWarnings("unchecked") ObjectProvider<UserNotificationService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(inbox);
        NotificationOutboxService producer = new NotificationOutboxService(outbox, null, null, null, provider);
        producer.configureDelivery(config, new AccountNotificationService(users, mongo), sessions);
        when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));

        NotificationOutboxItem item = producer.enqueue(intent(List.of("allowed", "denied", "absent"))).orElseThrow();

        assertThat(item.getFirebaseDeliveryRequested()).isTrue();
        assertThat(item.getFirebaseDevices()).containsEntry("denied", Map.of()).containsEntry("absent", Map.of());
        assertThat(item.getFirebaseDevices().get("allowed"))
                .containsExactlyEntriesOf(Map.of("phone", NotificationOutboxService.sha256("test-token")));
        assertThat(item.getFirebaseDevices().toString()).doesNotContain("test-token", "denied-token");
        assertThat(item.getRecipientUserIds()).containsExactly("allowed", "denied", "absent");
        verify(inbox).materialize(item);
        verify(users).findByUserIdIn(List.of("allowed", "denied", "absent"));
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void missingLocationSkipsPushWithBoundedAccountAndDeviceLookups() {
        config.setFirebaseEnabled(true);
        Users missing = user("missing", device("phone", "missing-token", true));
        missing.setLocationCoordinates(null);
        Users located = user("located", device("phone", "located-token", true));
        when(users.findByUserIdIn(List.of("missing", "located"))).thenReturn(List.of(missing, located));

        NotificationOutboxItem item = producer().enqueue(intent(List.of("missing", "located"))).orElseThrow();

        assertThat(item.getFirebaseDevices().get("missing")).isEmpty();
        assertThat(item.getFirebaseDevices().get("located")).containsKey("phone");
        verify(users).findByUserIdIn(List.of("missing", "located"));
        verifyNoMoreInteractions(users);
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void workerDoesNotSendQueuedPushAfterLocationIsRemoved() {
        NotificationOutboxItem item = queued(true,
                Map.of("viewer", Map.of("phone", NotificationOutboxService.sha256("test-token"))));
        Users viewer = user("viewer", device("phone", "test-token", true));
        viewer.setLocationCoordinates(null);
        when(users.findByUserIdIn(List.of("viewer"))).thenReturn(List.of(viewer));

        worker(item).processDueNotifications();

        verifyNoInteractions(gateway);
        verify(users).findByUserIdIn(List.of("viewer"));
        verifyNoMoreInteractions(users);
        assertThat(completionUpdates()).anySatisfy(update -> {
            assertThat(update.getString("status")).isEqualTo("sent");
            assertThat(update.getInteger("tokenSuccessCount")).isZero();
        });
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void unavailableFirebaseDecisionSurvivesActivationAndRecipientMerge() {
        NotificationOutboxService producer = producer();
        NotificationOutboxItem first = producer.enqueue(intent(List.of("viewer"))).orElseThrow();
        String syncKey = first.getSyncKey();
        when(outbox.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                anyString(), anyCollection(), anyString())).thenReturn(Optional.of(first));
        config.setFirebaseEnabled(true);

        NotificationOutboxItem merged = producer.enqueue(intent(List.of("viewer", "other"))).orElseThrow();

        assertThat(merged.getId()).isEqualTo(first.getId());
        assertThat(merged.getSyncKey()).isEqualTo(syncKey);
        assertThat(merged.getFirebaseDeliveryRequested()).isFalse();
        assertThat(merged.getFirebaseDevices()).containsEntry("viewer", Map.of()).containsEntry("other", Map.of());
        verify(users).findByUserIdIn(List.of("viewer"));
        verify(users).findByUserIdIn(List.of("viewer", "other"));
        verifyNoMoreInteractions(users);
        verifyNoInteractions(sessions);
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void deniedRecipientIsNotReevaluatedWhenThePendingMessageIsMerged() {
        config.setFirebaseEnabled(true);
        when(users.findByUserIdIn(List.of("viewer"))).thenReturn(List.of(user("viewer", device("phone", "test-token", false))));
        NotificationOutboxService producer = producer();
        NotificationOutboxItem first = producer.enqueue(intent(List.of("viewer"))).orElseThrow();
        when(outbox.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                anyString(), anyCollection(), anyString())).thenReturn(Optional.of(first));
        when(users.findByUserIdIn(List.of("viewer", "other"))).thenReturn(List.of(
                user("viewer", device("phone", "test-token", true)),
                user("other", device("other-phone", "other-token", true))));

        NotificationOutboxItem merged = producer.enqueue(intent(List.of("viewer", "other"))).orElseThrow();

        assertThat(merged.getFirebaseDevices().get("viewer")).isEmpty();
        assertThat(merged.getFirebaseDevices().get("other")).containsKey("other-phone");
        verify(users).findByUserIdIn(List.of("viewer"));
        verify(users).findByUserIdIn(List.of("viewer", "other"));
        verifyNoMoreInteractions(users);
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void workerCompletesAnExplicitNoFirebaseDecisionWithoutLookupOrSend() {
        NotificationOutboxItem item = queued(false, Map.of("viewer", Map.of()));
        worker(item).processDueNotifications();

        verifyNoInteractions(users, sessions, gateway);
        assertThat(completionUpdates()).anySatisfy(update -> {
            assertThat(update.getString("status")).isEqualTo("sent");
            assertThat(update.getInteger("tokenSuccessCount")).isZero();
            assertThat(update.getInteger("tokenFailureCount")).isZero();
        });
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void workerUsesSavedTargetsEvenWhenCurrentConfigurationHasChanged() throws Exception {
        NotificationOutboxItem item = queued(true,
                Map.of("viewer", Map.of("phone", NotificationOutboxService.sha256("test-token")), "denied", Map.of()));
        when(users.findByUserIdIn(List.of("viewer"))).thenReturn(List.of(user("viewer",
                device("phone", "test-token", true), device("new-phone", "new-token", true))));
        when(gateway.sendAll(anyList(), eq(List.of("test-token")))).thenReturn(FirebaseBatchSendResult.allSuccessful(1));

        worker(item).processDueNotifications();

        verify(gateway).sendAll(anyList(), eq(List.of("test-token")));
        verify(users).findByUserIdIn(List.of("viewer"));
        verifyNoInteractions(sessions);
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void revokedAndRotatedDevicesCannotReceiveAnOldQueuedMessage() {
        NotificationOutboxItem item = queued(true, Map.of("viewer", Map.of(
                "revoked", NotificationOutboxService.sha256("old-token"),
                "rotated", NotificationOutboxService.sha256("previous-token"))));
        when(users.findByUserIdIn(List.of("viewer"))).thenReturn(List.of(user("viewer",
                device("revoked", "old-token", false), device("rotated", "replacement-token", true))));

        worker(item).processDueNotifications();

        verifyNoInteractions(gateway);
        assertThat(completionUpdates()).anySatisfy(update -> assertThat(update.getInteger("tokenSuccessCount")).isZero());
    }

    @Test
    @org.junit.jupiter.api.Tag("standard")
    void retryKeepsTheOriginalIdentityAndDeliveryDecision() throws Exception {
        NotificationOutboxItem item = queued(true, Map.of("viewer", Map.of("phone", NotificationOutboxService.sha256("test-token"))));
        Map<String, Map<String, String>> plan = item.getFirebaseDevices();
        when(users.findByUserIdIn(List.of("viewer"))).thenReturn(List.of(user("viewer", device("phone", "test-token", true))));
        when(gateway.sendAll(anyList(), anyList())).thenThrow(new IllegalStateException("temporary provider failure"))
                .thenReturn(FirebaseBatchSendResult.allSuccessful(1));
        NotificationOutboxWorkerService worker = worker(item);
        worker.processDueNotifications();
        ReflectionTestUtils.setField(worker, "lastScheduledPollAt", Instant.EPOCH);
        worker.processDueNotifications();

        verify(gateway, times(2)).sendAll(anyList(), eq(List.of("test-token")));
        verify(outbox, times(2)).findById("queued-item");
        assertThat(item.getFirebaseDeliveryRequested()).isTrue();
        assertThat(item.getFirebaseDevices()).isEqualTo(plan);
        assertThat(completionUpdates()).allSatisfy(update ->
                assertThat(update).doesNotContainKeys("firebaseDeliveryRequested", "firebaseDevices", "syncKey", "_id"));
        assertThat(completionUpdates()).anySatisfy(update -> {
            assertThat(update.getString("status")).isEqualTo("pending");
            assertThat(update.getInteger("retryCount")).isEqualTo(1);
        });
    }

    @Test

    @org.junit.jupiter.api.Tag("integration")
    void mongoMergePreservesSavedDecisionsAndAddsOnlyNewRecipients() {
        String uri = System.getenv("QA_NOTIFICATION_MONGO_URI");
        org.junit.jupiter.api.Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String database = System.getenv("QA_NOTIFICATION_MONGO_DATABASE");
        org.junit.jupiter.api.Assumptions.assumeTrue("demo_db".equals(database) || "e2e_db".equals(database));
        String collection = "qa_notification_decision_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var settings = com.mongodb.MongoClientSettings.builder().applyConnectionString(new com.mongodb.ConnectionString(uri));
        String username = System.getenv("MONGO_APP_USERNAME");
        String password = System.getenv("MONGO_APP_PASSWORD");
        if (username != null && password != null) {
            settings.credential(com.mongodb.MongoCredential.createCredential(username, database, password.toCharArray()));
        }
        try (com.mongodb.client.MongoClient client = com.mongodb.client.MongoClients.create(settings.build())) {
            MongoTemplate actualMongo = new MongoTemplate(client, database) {
                @Override public String getCollectionName(Class<?> type) { return collection; }
            };
            try {
                config.setFirebaseEnabled(true);
                when(users.findByUserIdIn(anyList())).thenAnswer(call -> {
                    List<String> ids = call.getArgument(0);
                    return ids.stream().map(id -> user(id, device(id + "-phone", id + "-token", !id.equals("viewer")))).toList();
                });
                NotificationOutboxService producer = producer();
                producer.setMongoTemplate(actualMongo);
                when(outbox.save(any())).thenAnswer(call -> actualMongo.save(call.getArgument(0, NotificationOutboxItem.class)));
                when(outbox.findById(anyString())).thenAnswer(call -> Optional.ofNullable(
                        actualMongo.findById(call.getArgument(0, String.class), NotificationOutboxItem.class)));
                NotificationOutboxItem first = producer.enqueue(intent(List.of("viewer"))).orElseThrow();
                // Both merges see the same stale initial recipient list; the database must preserve both additions.
                when(outbox.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                        anyString(), anyCollection(), anyString())).thenReturn(Optional.of(first));
                producer.enqueue(intent(List.of("viewer", "second")));
                producer.enqueue(intent(List.of("viewer", "third")));
                NotificationOutboxItem stored = actualMongo.findById(first.getId(), NotificationOutboxItem.class);
                assertThat(stored.getSyncKey()).isEqualTo(first.getSyncKey());
                assertThat(stored.getFirebaseDeliveryRequested()).isTrue();
                assertThat(stored.getRecipientUserIds()).containsExactlyInAnyOrder("viewer", "second", "third");
                assertThat(stored.getFirebaseDevices().get("viewer")).isEmpty();
                assertThat(stored.getFirebaseDevices().get("second")).containsKey("second-phone");
                assertThat(stored.getFirebaseDevices().get("third")).containsKey("third-phone");
                assertThat(actualMongo.count(new Query(), NotificationOutboxItem.class)).isEqualTo(1);
            } finally {
                client.getDatabase(database).getCollection(collection).drop();
            }
        }
    }

    private NotificationOutboxService producer() {
        when(outbox.save(any())).thenAnswer(call -> call.getArgument(0));
        NotificationOutboxService producer = new NotificationOutboxService(outbox);
        producer.configureDelivery(config, new AccountNotificationService(users, mongo), sessions);
        return producer;
    }

    private NotificationOutboxWorkerService worker(NotificationOutboxItem item) {
        when(mongo.exists(any(Query.class), eq(NotificationOutboxItem.class))).thenReturn(true);
        when(mongo.find(any(Query.class), eq(NotificationOutboxItem.class))).thenReturn(List.of(item));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(NotificationOutboxItem.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(outbox.findById(item.getId())).thenReturn(Optional.of(item));
        NotificationOutboxWorkerService worker = new NotificationOutboxWorkerService(config, outbox, new AccountNotificationService(users, mongo), sessions, mongo, null, null,
                mock(AdminTelemetryService.class), gateway, 50, 500, 5, 300000L, 60000L, 0, 24, null);
        var registry = mock(NotificationRuleRegistryService.class);
        org.mockito.Mockito.doAnswer(call -> { com.raxim.myscoutee.domain.platform.api.JobGroupContext.run(null, call.getArgument(1, Runnable.class)); return null; }).when(registry).forEachGroup(anyString(), any(Runnable.class));
        @SuppressWarnings("unchecked") ObjectProvider<NotificationRuleRegistryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        worker.setNotificationRuleRegistryServiceProvider(provider);
        return worker;
    }

    private List<Document> completionUpdates() {
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongo, atLeastOnce()).updateFirst(any(Query.class), updates.capture(), eq(NotificationOutboxItem.class));
        return updates.getAllValues().stream().map(update -> (Document) update.getUpdateObject().get("$set")).toList();
    }

    private NotificationOutboxItem queued(boolean requested, Map<String, Map<String, String>> devices) {
        NotificationOutboxItem item = new NotificationOutboxItem();
        item.setId("queued-item");
        item.setSyncKey("unchanged-logical-key");
        item.setNotificationType("chat-message");
        item.setStatus("pending");
        item.setDeliveryStrategy("direct");
        item.setFirebaseDeliveryRequested(requested);
        item.setFirebaseDevices(devices);
        item.setRecipientUserIds(List.copyOf(devices.keySet()));
        item.setPayload(Map.of());
        return item;
    }

    private NotificationOutboxService.NotificationIntent intent(List<String> recipients) {
        return new NotificationOutboxService.NotificationIntent("chat-message", "chat", "chat-1", "users", recipients,
                "Chat", "Message", "/game", null, null, "chat-1", Map.of("chatId", "chat-1"), "sender");
    }

    private Users user(String id, Users.UserDeviceRecord... devices) {
        Users user = new Users(); user.setUserId(id); user.setDevices(List.of(devices));
        user.setLocationCoordinates(new Users.LocationCoordinatesRecord(30.2672, -97.7431));
        return user;
    }

    private Users.UserDeviceRecord device(String id, String token, boolean enabled) {
        Users.UserDeviceRecord device = new Users.UserDeviceRecord();
        device.setDeviceId(id); device.setFirebaseToken(token); device.setNotificationsEnabled(enabled); return device;
    }
}
