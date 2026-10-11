package com.raxim.myscoutee.domain.notifications.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface NotificationDelivery {
    Optional<? extends Receipt> enqueue(NotificationIntent intent);
    Optional<? extends Receipt> enqueueOnce(String operationId, NotificationIntent intent);

    interface Receipt {
        String getId();
        String getSyncKey();
    }

    record NotificationIntent(
            String type,
            String entityType,
            String entityId,
            String recipientScope,
            Collection<String> recipientUserIds,
            String title,
            String body,
            String clickUrl,
            String iconUrl,
            String badgeUrl,
            String tag,
            Map<String, String> payload,
            String createdUser) {
    }
}
