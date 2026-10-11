package com.raxim.myscoutee.domain.notifications.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.notifications.document.UserNotification;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications.Sender;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationDto;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;

@Component
public class UserNotificationMapper {
    private final AccountNotifications accounts;

    public UserNotificationMapper(AccountNotifications accounts) {
        this.accounts = accounts;
    }

    public List<UserNotificationDto> toDtos(List<UserNotification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        List<String> senderUserIds = notifications.stream()
                .filter(Objects::nonNull)
                .map(UserNotification::getSenderUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, Sender> usersById = senderUserIds.isEmpty()
                ? Map.of()
                : accounts.senders(senderUserIds).stream()
                        .filter(Objects::nonNull)
                        .filter(user -> StringUtils.hasText(user.userId()))
                        .collect(Collectors.toMap(
                                Sender::userId,
                                Function.identity(),
                                (left, right) -> left,
                                LinkedHashMap::new));
        return notifications.stream()
                .filter(Objects::nonNull)
                .map(notification -> toDto(
                        notification,
                        StringUtils.hasText(notification.getSenderUserId())
                                ? usersById.get(notification.getSenderUserId())
                                : null))
                .toList();
    }

    public UserNotificationDto toDto(UserNotification notification) {
        if (notification == null) {
            return null;
        }
        Sender sender = StringUtils.hasText(notification.getSenderUserId())
                ? accounts.senders(List.of(notification.getSenderUserId())).stream().findFirst().orElse(null)
                : null;
        return toDto(notification, sender);
    }

    private UserNotificationDto toDto(UserNotification notification, Sender sender) {
        Map<String, String> payload = notification.getPayload() == null
                ? Map.of()
                : Map.copyOf(notification.getPayload());
        return new UserNotificationDto(
                safeText(notification.getId()),
                safeText(notification.getRecipientUserId()),
                safeText(notification.getKind()),
                safeText(notification.getCategory()),
                safeText(notification.getTitle()),
                safeText(notification.getMessage()),
                safeText(notification.getCreatedDate()),
                nullableText(notification.getReadAtIso()),
                nullableText(notification.getSenderUserId()),
                firstNonBlank(sender == null ? null : sender.name(), payload.get("senderName")),
                firstNonBlank(sender == null ? null : sender.avatarUrl(), payload.get("senderAvatarUrl")),
                safeText(notification.getActionPath()),
                safeText(notification.getSourceType()),
                safeText(notification.getSourceId()),
                payload,
                notification.getOccurrenceCount() == null
                        ? 1
                        : Math.max(1, notification.getOccurrenceCount()),
                notification.getRevision() == null
                        ? 1L
                        : Math.max(1L, notification.getRevision()));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullableText(String value) {
        String normalized = safeText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
