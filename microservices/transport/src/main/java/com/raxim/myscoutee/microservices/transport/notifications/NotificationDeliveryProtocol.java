package com.raxim.myscoutee.microservices.transport.notifications;

import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.NotificationIntent;
import com.raxim.myscoutee.microservices.transport.platform.JobAudienceProtocol;

public final class NotificationDeliveryProtocol {
    public static final String ENQUEUE = "/_internal/v1/notifications/outbox/enqueue";
    public static final String ONCE = "/_internal/v1/notifications/outbox/enqueue-once";
    public static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private NotificationDeliveryProtocol() {}
    public record Command(JobAudienceProtocol.Context context, String operationId, NotificationIntent intent) {}
    public record ReceiptDto(String id, String syncKey) implements NotificationDelivery.Receipt {
        @Override public String getId() { return id; }
        @Override public String getSyncKey() { return syncKey; }
    }
    public record Result(Boolean completed, ReceiptDto receipt) {}
    public record Error(String code) {}
    public static String[] paths() { return new String[]{ENQUEUE, ONCE}; }
    private static boolean bounded(String value, int limit) { return value == null || value.length() <= limit; }
    public static boolean valid(Command command) {
        if (command == null || !JobAudienceProtocol.valid(command.context()) || !bounded(command.operationId(), 4096)) return false;
        var intent = command.intent();
        return intent == null || bounded(intent.type(), 512) && bounded(intent.entityType(), 512)
                && bounded(intent.entityId(), 512) && bounded(intent.recipientScope(), 512)
                && (intent.recipientUserIds() == null || intent.recipientUserIds().size() <= 4096
                    && intent.recipientUserIds().stream().allMatch(id -> bounded(id, 512)))
                && bounded(intent.title(), 16384) && bounded(intent.body(), 65536)
                && bounded(intent.clickUrl(), 4096) && bounded(intent.iconUrl(), 4096) && bounded(intent.badgeUrl(), 4096)
                && bounded(intent.tag(), 4096) && bounded(intent.createdUser(), 512)
                && (intent.payload() == null || intent.payload().size() <= 256 && intent.payload().entrySet().stream()
                    .allMatch(entry -> entry.getKey() != null && bounded(entry.getKey(), 512) && bounded(entry.getValue(), 16384)));
    }
}
