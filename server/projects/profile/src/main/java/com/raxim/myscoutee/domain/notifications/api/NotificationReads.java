package com.raxim.myscoutee.domain.notifications.api;
public interface NotificationReads {
    com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationsPageResponseDto queryPage(
            String recipientUserId, String bucket, Integer limit, String cursor, String workspace);
}
