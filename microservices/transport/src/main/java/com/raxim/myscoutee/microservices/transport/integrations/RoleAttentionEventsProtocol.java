package com.raxim.myscoutee.microservices.transport.integrations;

import com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents;

public final class RoleAttentionEventsProtocol {
    public static final String ENQUEUE = "/_internal/v1/integrations/role-attention-events/enqueue";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private RoleAttentionEventsProtocol() {}
    public static String[] paths() { return new String[]{ENQUEUE}; }
    public record Error(String code) {}
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(com.raxim.myscoutee.domain.platform.api.JobGroupContext.isActive(), com.raxim.myscoutee.domain.platform.api.JobGroupContext.groupId()); }
    }
    private static boolean text(String value, int limit) { return value == null || value.length() <= limit; }
    private static boolean scope(Scope value) { return value != null && value.active() != null && text(value.groupId(), 512) && (value.active() || value.groupId() == null); }
    private static boolean ids(java.util.Collection<String> values) { return values != null && values.size() <= 4096 && values.stream().allMatch(value -> text(value, 512)); }
    public record Receipt(Boolean completed) {}
    public record Command(Scope scope, RoleAttentionEvents.RoleAttention notification) {}
    public static boolean valid(Command q) { return q != null && scope(q.scope()) && q.notification() != null && text(q.notification().id(), 512) && text(q.notification().entityType(), 128) && text(q.notification().notificationType(), 128) && ids(q.notification().recipientUserIds()); }
}
