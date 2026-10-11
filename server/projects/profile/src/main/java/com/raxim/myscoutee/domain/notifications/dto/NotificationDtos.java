package com.raxim.myscoutee.domain.notifications.dto;

import java.util.List;
import java.util.Map;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record UserNotificationDto(
            String id,
            String recipientUserId,
            String kind,
            String category,
            String title,
            String message,
            String createdAtIso,
            String readAtIso,
            String senderUserId,
            String senderName,
            String senderAvatarUrl,
            String actionPath,
            String sourceType,
            String sourceId,
            Map<String, String> payload,
            Integer occurrenceCount,
            Long revision) {
    }

    public record UserNotificationSyncKnownItemDto(String id, Long revision) {
    }

    public record UserNotificationSyncBoundaryDto(String id, String createdAtIso) {
    }

    public record UserNotificationsSyncRequestDto(
            String bucket,
            Integer limit,
            List<UserNotificationSyncKnownItemDto> knownItems,
            UserNotificationSyncBoundaryDto loadedTail, String workspace) {
        public UserNotificationsSyncRequestDto(String bucket, Integer limit, List<UserNotificationSyncKnownItemDto> knownItems,
                UserNotificationSyncBoundaryDto loadedTail) { this(bucket, limit, knownItems, loadedTail, null); }
    }

    public record UserNotificationsSyncResponseDto(
            List<UserNotificationDto> upserts,
            List<String> removedIds,
            Long total,
            Integer unreadCount,
            Boolean muted) {
    }

    public record UserNotificationsPageResponseDto(
            List<UserNotificationDto> records,
            Long total,
            String nextCursor,
            Integer unreadCount,
            Boolean muted) {
    }

    public record UserNotificationReadResponseDto(
            UserNotificationDto notification,
            Integer unreadCount) {
    }

    public record UserNotificationPreferenceRequestDto(
            Boolean muted) {
    }

    public record UserNotificationPreferenceDto(
            Boolean muted) {
    }
}
