package com.raxim.myscoutee.microservices.transport.notifications;

import java.util.*;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

public final class NotificationPrivacyProtocol {
    public static final String PURGE_DELIVERY_DATA = "/_internal/v1/notifications/notification-privacy/purge-delivery-data";
    public static final String ANONYMIZE_RULE_AUTHORS = "/_internal/v1/notifications/notification-privacy/anonymize-rule-authors";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private NotificationPrivacyProtocol() {}
    public static String[] paths() { return new String[]{PURGE_DELIVERY_DATA, ANONYMIZE_RULE_AUTHORS}; }
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(JobGroupContext.isActive(), JobGroupContext.groupId()); }
    }
    public record UserCommand(Scope scope, String userId) {}
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    private static boolean id(String value) { return value != null && !value.isBlank() && value.length() <= 512; }
    private static boolean scope(Scope value) {
        return value != null && value.active() != null && (value.groupId() == null || id(value.groupId()))
                && (value.active() || value.groupId() == null);
    }
    public static boolean valid(UserCommand value) { return value != null && scope(value.scope()) && id(value.userId()); }
}
