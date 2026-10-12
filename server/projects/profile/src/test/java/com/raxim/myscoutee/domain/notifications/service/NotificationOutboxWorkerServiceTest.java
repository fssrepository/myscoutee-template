package com.raxim.myscoutee.domain.notifications.service;

import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.result.UpdateResult;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem;
import com.raxim.myscoutee.domain.notifications.document.NotificationRuleConfig;
import com.raxim.myscoutee.domain.notifications.repository.NotificationOutboxRepository;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.accounts.service.AccountNotificationService;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.administration.service.AdminTelemetryService;
import com.raxim.myscoutee.domain.chat.service.ChatWebSocketSessionRegistry;

@org.junit.jupiter.api.Tag("standard")
class NotificationOutboxWorkerServiceTest {
    @Test
    void inactiveFirebaseStillRecordsTheEnabledScheduledRuleWithoutTouchingPendingWork() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setFirebaseEnabled(false);
        NotificationOutboxRepository outboxRepository =
                org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        UsersRepository usersRepository = org.mockito.Mockito.mock(UsersRepository.class);
        ChatWebSocketSessionRegistry sessionRegistry =
                org.mockito.Mockito.mock(ChatWebSocketSessionRegistry.class);
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        when(mongoTemplate.exists(any(Query.class), eq(NotificationOutboxItem.class))).thenReturn(true);
        FirebasePushDelivery gateway =
                org.mockito.Mockito.mock(FirebasePushDelivery.class);
        NotificationRuleRegistryService registry =
                org.mockito.Mockito.mock(NotificationRuleRegistryService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationRuleRegistryService> registryProvider =
                org.mockito.Mockito.mock(ObjectProvider.class);
        NotificationRuleConfig rule = new NotificationRuleConfig();
        rule.setRuleKey(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX);
        rule.setEnabled(true);
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        org.mockito.Mockito.doAnswer(call -> { com.raxim.myscoutee.domain.platform.api.JobGroupContext.run(null, call.getArgument(1, Runnable.class)); return null; }).when(registry).forEachGroup(any(String.class), any(Runnable.class));
        when(registry.findRule(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX))
                .thenReturn(Optional.of(rule));
        when(registry.shouldRunScheduledRule(
                eq(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX),
                any(Instant.class)))
                .thenReturn(true);
        when(registry.beginScheduledRun(
                eq(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX),
                any(String.class),
                eq("notification-outbox"),
                any(Instant.class)))
                .thenReturn(rule);
        NotificationOutboxWorkerService service = new NotificationOutboxWorkerService(
                configProperties,
                outboxRepository,
                new AccountNotificationService(usersRepository, mongoTemplate),
                sessionRegistry,
                mongoTemplate,
                null,
                null,
                org.mockito.Mockito.mock(AdminTelemetryService.class),
                gateway,
                50,
                500,
                5,
                300_000L,
                60_000L,
                0,
                24,
                null);
        service.setNotificationRuleRegistryServiceProvider(registryProvider);

        service.processDueNotifications();

        ArgumentCaptor<Instant> beginAt = ArgumentCaptor.forClass(Instant.class);
        verify(registry).beginScheduledRun(
                eq(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX),
                any(String.class),
                eq("notification-outbox"),
                beginAt.capture());
        ArgumentCaptor<Instant> recordedAt = ArgumentCaptor.forClass(Instant.class);
        verify(registry).recordRun(
                eq(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX),
                eq(0),
                eq("completed"),
                eq("Processed pending notification outbox delivery decisions."),
                eq("notification-outbox"),
                recordedAt.capture());
        assertThat(recordedAt.getValue()).isEqualTo(beginAt.getValue());
        verify(registry, never()).adjustPendingCount(
                any(String.class),
                anyInt(),
                any(String.class));
        verifyNoInteractions(outboxRepository, usersRepository, gateway);
        verify(mongoTemplate).find(any(Query.class), eq(NotificationOutboxItem.class));
    }

    @Test
    void inactiveFirebaseDoesNothingUntilTheEnabledRuleIsDue() {
        NotificationRuleRegistryService registry =
                org.mockito.Mockito.mock(NotificationRuleRegistryService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationRuleRegistryService> registryProvider =
                org.mockito.Mockito.mock(ObjectProvider.class);
        NotificationRuleConfig rule = new NotificationRuleConfig();
        rule.setRuleKey(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX);
        rule.setEnabled(true);
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        org.mockito.Mockito.doAnswer(call -> { com.raxim.myscoutee.domain.platform.api.JobGroupContext.run(null, call.getArgument(1, Runnable.class)); return null; }).when(registry).forEachGroup(any(String.class), any(Runnable.class));
        when(registry.findRule(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX))
                .thenReturn(Optional.of(rule));
        NotificationOutboxWorkerService service = service(
                org.mockito.Mockito.mock(ChatWebSocketSessionRegistry.class));
        service.setNotificationRuleRegistryServiceProvider(registryProvider);

        MongoTemplate mongo = (MongoTemplate) org.springframework.test.util.ReflectionTestUtils.getField(service, "mongoTemplate");
        when(mongo.exists(any(Query.class), eq(NotificationOutboxItem.class))).thenReturn(true);

        service.processDueNotifications();

        verify(registry, never()).beginScheduledRun(
                any(String.class),
                any(String.class),
                any(String.class),
                any(Instant.class));
        verify(registry, never()).recordRun(
                any(String.class),
                anyInt(),
                any(String.class),
                any(String.class),
                any(String.class),
                any(Instant.class));
    }

    @Test
    void chatDeliverySuppressesForegroundUsersImmediatelyBeforeDeviceLookup() {
        ChatWebSocketSessionRegistry sessionRegistry = org.mockito.Mockito.mock(ChatWebSocketSessionRegistry.class);
        when(sessionRegistry.isUserConnected("chat-1", "connected-viewer")).thenReturn(true);
        NotificationOutboxWorkerService service = service(sessionRegistry);
        NotificationOutboxItem item = item("chat-message", List.of(
                "connected-viewer",
                "offline-viewer"));
        item.setEntityType("chat");
        item.setEntityId("chat-1");
        item.setPayload(Map.of("chatId", "chat-1"));

        List<String> recipients = service.deliveryRecipientUserIds(item);

        assertThat(recipients).containsExactly("offline-viewer");
        verify(sessionRegistry).isUserConnected("chat-1", "connected-viewer");
        verify(sessionRegistry).isUserConnected("chat-1", "offline-viewer");
    }

    @Test
    void nonChatDeliveryKeepsItsAudienceUnchanged() {
        ChatWebSocketSessionRegistry sessionRegistry = org.mockito.Mockito.mock(ChatWebSocketSessionRegistry.class);
        NotificationOutboxWorkerService service = service(sessionRegistry);
        NotificationOutboxItem item = item("event-invite", List.of("viewer-1", "viewer-2"));
        item.setEntityType("event");
        item.setEntityId("event-1");

        List<String> recipients = service.deliveryRecipientUserIds(item);

        assertThat(recipients).containsExactly("viewer-1", "viewer-2");
        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void workerLooksUpDevicesOnlyForChatUsersNotConnectedAtDeliveryTime() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setFirebaseEnabled(true);
        NotificationOutboxRepository outboxRepository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        UsersRepository usersRepository = org.mockito.Mockito.mock(UsersRepository.class);
        ChatWebSocketSessionRegistry sessionRegistry = org.mockito.Mockito.mock(ChatWebSocketSessionRegistry.class);
        MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
        NotificationOutboxItem item = item(
                "chat-message",
                List.of("connected-viewer", "offline-viewer"));
        item.setId("outbox-1");
        item.setStatus(NotificationOutboxItem.STATUS_PENDING);
        item.setDeliveryStrategy("direct");
        item.setEntityId("chat-1");
        when(sessionRegistry.isUserConnected("chat-1", "connected-viewer")).thenReturn(true);
        when(mongoTemplate.find(any(Query.class), eq(NotificationOutboxItem.class))).thenReturn(List.of(item));
        when(mongoTemplate.exists(any(Query.class), eq(NotificationOutboxItem.class))).thenReturn(true);
        when(mongoTemplate.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(NotificationOutboxItem.class))).thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(outboxRepository.findById("outbox-1")).thenReturn(Optional.of(item));
        when(usersRepository.findByUserIdIn(List.of("offline-viewer"))).thenReturn(List.of());
        NotificationOutboxWorkerService service = new NotificationOutboxWorkerService(
                configProperties,
                outboxRepository,
                new AccountNotificationService(usersRepository, mongoTemplate),
                sessionRegistry,
                mongoTemplate,
                null,
                null,
                org.mockito.Mockito.mock(AdminTelemetryService.class),
                org.mockito.Mockito.mock(FirebasePushDelivery.class),
                50,
                500,
                5,
                300_000L,
                60_000L,
                0,
                24,
                null);

        var registry = org.mockito.Mockito.mock(NotificationRuleRegistryService.class);
        org.mockito.Mockito.doAnswer(call -> { com.raxim.myscoutee.domain.platform.api.JobGroupContext.run(null, call.getArgument(1, Runnable.class)); return null; }).when(registry).forEachGroup(any(String.class), any(Runnable.class));
        @SuppressWarnings("unchecked") ObjectProvider<NotificationRuleRegistryService> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        service.setNotificationRuleRegistryServiceProvider(provider);
        service.processDueNotifications();

        verify(usersRepository).findByUserIdIn(List.of("offline-viewer"));
    }

    @Test
    void legacyRecordsUseTheirSnapshottedDeploymentBrand() {
        NotificationOutboxWorkerService service = service(
                org.mockito.Mockito.mock(
                        ChatWebSocketSessionRegistry.class));
        NotificationOutboxItem item = item(
                "scheduled-template",
                List.of("viewer-1"));
        item.setIconUrl("/assets/logo/heart.webp");
        item.setBadgeUrl("/assets/logo/heart.webp");
        item.setPayload(Map.of(
                "productName", "Community Hub",
                "productLogoUrl", "/api/assets/community.webp",
                "productBrandingRevision", "12"));

        Map<String, String> payload = service.payloadFor(item);

        assertThat(payload)
                .containsEntry("title", "Community Hub")
                .containsEntry("icon", "/api/assets/community.webp")
                .containsEntry("badge", "/api/assets/community.webp");
    }

    @Test
    void emptySavedLogoDoesNotReappearInAnExistingPushPayload() {
        var service = service(org.mockito.Mockito.mock(ChatWebSocketSessionRegistry.class));
        var item = item("chat-message", List.of("viewer-1"));
        item.setPayload(Map.of("productName", "Configured", "productLogoUrl", "",
                "productBrandingRevision", "9", "icon", "/assets/logo/heart.webp",
                "badge", "/assets/logo/heart.webp"));
        assertThat(service.payloadFor(item)).doesNotContainKeys("icon", "badge");
        item.setIconUrl("/media/sender.webp");
        assertThat(service.payloadFor(item)).containsEntry("icon", "/media/sender.webp")
                .doesNotContainKey("badge");
    }

    private NotificationOutboxWorkerService service(ChatWebSocketSessionRegistry sessionRegistry) {
        return new NotificationOutboxWorkerService(
                new ConfigProperties(),
                org.mockito.Mockito.mock(NotificationOutboxRepository.class),
                org.mockito.Mockito.mock(AccountNotifications.class),
                sessionRegistry,
                org.mockito.Mockito.mock(MongoTemplate.class),
                null,
                null,
                org.mockito.Mockito.mock(AdminTelemetryService.class),
                org.mockito.Mockito.mock(FirebasePushDelivery.class),
                50,
                500,
                5,
                300_000L,
                60_000L,
                0,
                24,
                null);
    }

    private NotificationOutboxItem item(String type, List<String> recipientUserIds) {
        NotificationOutboxItem item = new NotificationOutboxItem();
        item.setNotificationType(type);
        item.setRecipientUserIds(recipientUserIds);
        return item;
    }
}
