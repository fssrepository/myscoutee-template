package com.raxim.myscoutee.domain.notifications.api;

import java.util.List;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.*;

public interface NotificationInbox extends NotificationReads {
    UserNotificationsPageResponseDto queryPage(
            String recipientUserId,
            String requestedBucket,
            Integer requestedLimit,
            String cursor);
    UserNotificationsPageResponseDto queryPage(String recipientUserId, String requestedBucket, Integer requestedLimit, String cursor, String workspace);
    UserNotificationReadResponseDto markRead(String recipientUserId, String notificationId);
    UserNotificationsSyncResponseDto sync(
            String recipientUserId,
            UserNotificationsSyncRequestDto request);
    int markChatMessagesRead(String recipientUserId, String chatId, List<String> messageIds);
    int markChatRead(String recipientUserId, String chatId);
    UserNotificationPreferenceDto updateMutedPreference(String recipientUserId, Boolean muted);
    int currentUnreadCount(String recipientUserId);
}
