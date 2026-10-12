package com.raxim.myscoutee.domain.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem;
import com.raxim.myscoutee.domain.notifications.repository.NotificationOutboxRepository;
import com.raxim.myscoutee.common.config.PlatformParametersService;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.NotificationIntent;
import com.raxim.myscoutee.domain.platform.service.DeploymentBrandingResolver;
import com.raxim.myscoutee.domain.platform.api.DeploymentBranding.ResolvedDeploymentBranding;

@org.junit.jupiter.api.Tag("standard")
class NotificationOutboxServiceTest {
    @Test
    void enqueueCollapsesPendingMessagesAndMergesRecipients() {
        NotificationOutboxRepository repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        NotificationOutboxItem existing = new NotificationOutboxItem();
        existing.setId("n-1");
        existing.setStatus(NotificationOutboxItem.STATUS_PENDING);
        existing.setRecipientUserIds(List.of("u1"));
        existing.setNextRetryAt("2026-05-11T12:00:00Z");
        existing.setPayload(Map.of(
                "notification_title_key", "notification.kind.asset-review.title",
                "notification_message_key", "notification.kind.asset-review.message"));
        when(repository.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(anyString(), anyCollection(), anyString()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(NotificationOutboxItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationOutboxService service = new NotificationOutboxService(repository);

        service.enqueue(new NotificationIntent(
                "asset-review",
                "asset",
                "asset-1",
                "asset-active-managers",
                List.of("u1", "u2", "u2"),
                "Asset needs review",
                "Asset needs review.",
                "/game",
                "/asset.webp",
                "/badge.webp",
                "asset-review-asset-1",
                Map.of("assetId", "asset-1"),
                "worker"));

        ArgumentCaptor<NotificationOutboxItem> savedItem = ArgumentCaptor.forClass(NotificationOutboxItem.class);
        verify(repository).save(savedItem.capture());
        assertThat(savedItem.getValue().getRecipientUserIds()).containsExactly("u1", "u2");
        assertThat(savedItem.getValue().getStatus()).isEqualTo(NotificationOutboxItem.STATUS_PENDING);
        assertThat(savedItem.getValue().getPayload())
                .containsEntry("notification_title_key", "notification.kind.asset-review.title")
                .containsEntry("notification_message_key", "notification.kind.asset-review.message");
    }

    @Test
    void enqueueSkipsEmptyRecipients() {
        NotificationOutboxRepository repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        NotificationOutboxService service = new NotificationOutboxService(repository);

        Optional<NotificationOutboxItem> result = service.enqueue(new NotificationIntent(
                "asset-review",
                "asset",
                "asset-1",
                "asset-active-managers",
                List.of(" "),
                "Asset needs review",
                "Asset needs review.",
                "/game",
                null,
                null,
                null,
                Map.of(),
                "worker"));

        assertThat(result).isEmpty();
        verify(repository, never()).save(any(NotificationOutboxItem.class));
    }

    @Test
    void enqueuePreservesExplicitNotificationTranslationKeys() {
        NotificationOutboxRepository repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                anyString(), anyCollection(), anyString())).thenReturn(Optional.empty());
        when(repository.save(any(NotificationOutboxItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationOutboxService service = new NotificationOutboxService(repository);

        NotificationOutboxItem saved = service.enqueue(intent(
                List.of("u1"),
                Map.of(
                        "notification_title_key", "notification.asset.takeover.required.title",
                        "notification_message_key", "notification.asset.takeover.required.message")))
                .orElseThrow();

        assertThat(saved.getPayload())
                .containsEntry("notification_title_key", "notification.asset.takeover.required.title")
                .containsEntry("notification_message_key", "notification.asset.takeover.required.message");
    }

    @Test
    void enqueueUsesDirectDeliveryBelowMulticastThreshold() {
        NotificationOutboxRepository repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(anyString(), anyCollection(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationOutboxItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformParametersService parameters = org.mockito.Mockito.mock(PlatformParametersService.class);
        when(parameters.notificationMulticastThreshold(3)).thenReturn(3);
        NotificationOutboxService service = new NotificationOutboxService(repository, null, parameters);

        service.enqueue(intent(List.of("u1", "u2"), Map.of()));

        ArgumentCaptor<NotificationOutboxItem> savedItem = ArgumentCaptor.forClass(NotificationOutboxItem.class);
        verify(repository).save(savedItem.capture());
        assertThat(savedItem.getValue().getDeliveryStrategy()).isEqualTo("direct");
    }

    @Test
    void enqueueUsesMulticastDeliveryAtMulticastThreshold() {
        NotificationOutboxRepository repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(anyString(), anyCollection(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationOutboxItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformParametersService parameters = org.mockito.Mockito.mock(PlatformParametersService.class);
        when(parameters.notificationMulticastThreshold(3)).thenReturn(3);
        NotificationOutboxService service = new NotificationOutboxService(repository, null, parameters);

        service.enqueue(intent(List.of("u1", "u2", "u3"), Map.of()));

        ArgumentCaptor<NotificationOutboxItem> savedItem = ArgumentCaptor.forClass(NotificationOutboxItem.class);
        verify(repository).save(savedItem.capture());
        assertThat(savedItem.getValue().getDeliveryStrategy()).isEqualTo("multicast");
    }

    @Test
    void enqueuePayloadMulticastThresholdOverridesConfig() {
        NotificationOutboxRepository repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(anyString(), anyCollection(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationOutboxItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformParametersService parameters = org.mockito.Mockito.mock(PlatformParametersService.class);
        when(parameters.notificationMulticastThreshold(3)).thenReturn(10);
        NotificationOutboxService service = new NotificationOutboxService(repository, null, parameters);

        service.enqueue(intent(List.of("u1", "u2"), Map.of("multicastThreshold", "2")));

        ArgumentCaptor<NotificationOutboxItem> savedItem = ArgumentCaptor.forClass(NotificationOutboxItem.class);
        verify(repository).save(savedItem.capture());
        assertThat(savedItem.getValue().getDeliveryStrategy()).isEqualTo("multicast");
    }

    @Test
    void enqueueUpgradesCollapsedDirectItemToMulticastAfterRecipientMerge() {
        NotificationOutboxRepository repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        NotificationOutboxItem existing = new NotificationOutboxItem();
        existing.setId("n-1");
        existing.setStatus(NotificationOutboxItem.STATUS_PENDING);
        existing.setDeliveryStrategy("direct");
        existing.setRecipientUserIds(List.of("u1", "u2"));
        existing.setPayload(Map.of());
        when(repository.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(anyString(), anyCollection(), anyString()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(NotificationOutboxItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformParametersService parameters = org.mockito.Mockito.mock(PlatformParametersService.class);
        when(parameters.notificationMulticastThreshold(3)).thenReturn(3);
        NotificationOutboxService service = new NotificationOutboxService(repository, null, parameters);

        service.enqueue(intent(List.of("u3"), Map.of()));

        ArgumentCaptor<NotificationOutboxItem> savedItem = ArgumentCaptor.forClass(NotificationOutboxItem.class);
        verify(repository).save(savedItem.capture());
        assertThat(savedItem.getValue().getRecipientUserIds()).containsExactly("u1", "u2", "u3");
        assertThat(savedItem.getValue().getDeliveryStrategy()).isEqualTo("multicast");
    }

    @Test
    void enqueueMaterializesTheSavedOutboxItemIntoTheUserInbox() {
        NotificationOutboxRepository repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository.findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                anyString(),
                anyCollection(),
                anyString())).thenReturn(Optional.empty());
        when(repository.save(any(NotificationOutboxItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserNotificationService userNotificationService = org.mockito.Mockito.mock(UserNotificationService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserNotificationService> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(userNotificationService);
        NotificationOutboxService service = new NotificationOutboxService(
                repository,
                null,
                null,
                null,
                provider);

        NotificationOutboxItem saved = service.enqueue(intent(List.of("u1"), Map.of())).orElseThrow();

        verify(userNotificationService).materialize(saved);
    }

    @Test
    void enqueueSnapshotsDeploymentBrandingAndReplacesLegacyIcons() {
        NotificationOutboxRepository repository =
                org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository
                .findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                        anyString(),
                        anyCollection(),
                        anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationOutboxItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DeploymentBrandingResolver brandingResolver =
                org.mockito.Mockito.mock(DeploymentBrandingResolver.class);
        when(brandingResolver.resolve()).thenReturn(
                new ResolvedDeploymentBranding(
                        "Community Hub",
                        "/api/assets/community.webp",
                        9L));
        NotificationOutboxService service =
                new NotificationOutboxService(repository);
        service.setDeploymentBrandingResolver(brandingResolver);

        NotificationOutboxItem saved = service.enqueue(
                new NotificationIntent(
                        "deployment-news",
                        "announcement",
                        "a-1",
                        "users",
                        List.of("u1"),
                        null,
                        "A new update is available.",
                        "/game",
                        "/assets/logo/heart.webp",
                        "assets/logo/heart.png",
                        "deployment-news-a-1",
                        Map.of(),
                        "worker"))
                .orElseThrow();

        assertThat(saved.getTitle()).isEqualTo("Community Hub");
        assertThat(saved.getIconUrl())
                .isEqualTo("/api/assets/community.webp");
        assertThat(saved.getBadgeUrl())
                .isEqualTo("/api/assets/community.webp");
        assertThat(saved.getPayload())
                .containsEntry("productName", "Community Hub")
                .containsEntry(
                        "productLogoUrl",
                        "/api/assets/community.webp")
                .containsEntry("productBrandingRevision", "9");
    }

    @Test
    void enqueuePreservesAnExplicitContentIcon() {
        NotificationOutboxRepository repository =
                org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository
                .findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                        anyString(),
                        anyCollection(),
                        anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationOutboxItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DeploymentBrandingResolver brandingResolver =
                org.mockito.Mockito.mock(DeploymentBrandingResolver.class);
        when(brandingResolver.resolve()).thenReturn(
                new ResolvedDeploymentBranding(
                        "Community Hub",
                        "/api/assets/community.webp",
                        9L));
        NotificationOutboxService service =
                new NotificationOutboxService(repository);
        service.setDeploymentBrandingResolver(brandingResolver);

        NotificationOutboxItem saved = service.enqueue(
                new NotificationIntent(
                        "event-update",
                        "event",
                        "e-1",
                        "users",
                        List.of("u1"),
                        "Picnic changed",
                        "Open the event.",
                        "/game",
                        "/api/assets/picnic-cover.webp",
                        null,
                        "event-update-e-1",
                        Map.of(),
                        "worker"))
                .orElseThrow();

        assertThat(saved.getIconUrl())
                .isEqualTo("/api/assets/picnic-cover.webp");
        assertThat(saved.getBadgeUrl())
                .isEqualTo("/api/assets/community.webp");
    }

    @Test
    void enqueueKeepsACompleteBrandingSnapshotWithoutResolvingTheCurrentBrand() {
        NotificationOutboxRepository repository =
                org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository
                .findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                        anyString(),
                        anyCollection(),
                        anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(NotificationOutboxItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        DeploymentBrandingResolver brandingResolver =
                org.mockito.Mockito.mock(DeploymentBrandingResolver.class);
        NotificationOutboxService service =
                new NotificationOutboxService(repository);
        service.setDeploymentBrandingResolver(brandingResolver);

        NotificationOutboxItem saved = service.enqueue(
                new NotificationIntent(
                        "scheduled-template",
                        "notification-template",
                        "profile-reminder",
                        "scheduled-user",
                        List.of("u1", "u2"),
                        null,
                        "Complete your profile.",
                        "/game",
                        "/assets/logo/heart.webp",
                        "/assets/logo/heart.webp",
                        "scheduled-profile-reminder",
                        Map.of(
                                "product_name", "Queued Community",
                                "product_logo_url", "/api/assets/queued.webp",
                                "product_branding_revision", "17"),
                        "notification-scheduler"))
                .orElseThrow();

        verify(brandingResolver, never()).resolve();
        assertThat(saved.getTitle()).isEqualTo("Queued Community");
        assertThat(saved.getIconUrl()).isEqualTo("/api/assets/queued.webp");
        assertThat(saved.getBadgeUrl()).isEqualTo("/api/assets/queued.webp");
        assertThat(saved.getPayload())
                .containsEntry("productName", "Queued Community")
                .containsEntry("productLogoUrl", "/api/assets/queued.webp")
                .containsEntry("productBrandingRevision", "17");
    }

    @Test
    void emptyLogoSnapshotIsCompleteAndDoesNotReloadOrInventBranding() {
        var repository = org.mockito.Mockito.mock(NotificationOutboxRepository.class);
        when(repository.save(any(NotificationOutboxItem.class))).thenAnswer(call -> call.getArgument(0));
        var service = new NotificationOutboxService(repository);
        var branding = org.mockito.Mockito.mock(DeploymentBrandingResolver.class);
        service.setDeploymentBrandingResolver(branding);
        var saved = service.enqueue(new NotificationIntent("chat-message", "chat", "c1", "users",
                List.of("u1"), "Chat", "Message", "/home", null, null, "chat-c1",
                Map.of("productName", "Configured", "productLogoUrl", "", "productBrandingRevision", "9",
                        "icon", "/assets/logo/heart.webp", "badge", "/assets/logo/heart.webp"), "sender")).orElseThrow();
        verify(branding, never()).resolve();
        assertThat(saved.getPayload()).containsEntry("productLogoUrl", "")
                .doesNotContainKeys("icon", "badge");
    }

    private NotificationIntent intent(List<String> recipientUserIds, Map<String, String> payload) {
        return new NotificationIntent(
                "asset-review",
                "asset",
                "asset-1",
                "asset-active-managers",
                recipientUserIds,
                "Asset needs review",
                "Asset needs review.",
                "/game",
                "/asset.webp",
                "/badge.webp",
                "asset-review-asset-1",
                payload,
                "worker");
    }
}
