package com.raxim.myscoutee.microservices.transport.notifications;

import com.raxim.myscoutee.domain.notifications.api.NotificationReads;

public final class NotificationReadsProtocol {
    public static final String PAGE = "/_internal/v1/notifications/notification-reads/page";
    public static final String UNREAD = "/_internal/v1/notifications/notification-reads/unread";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    public static final String AUDIENCE = "notifications:notification-reads";
    private NotificationReadsProtocol() {}
    public static String[] paths() { return new String[]{PAGE, UNREAD}; }
    public record Error(String code) {}
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(com.raxim.myscoutee.domain.platform.api.JobGroupContext.isActive(), com.raxim.myscoutee.domain.platform.api.JobGroupContext.groupId()); }
    }
    private static boolean text(String value, int limit) { return value == null || value.length() <= limit; }
    private static boolean scope(Scope value) { return value != null && value.active() != null && text(value.groupId(), 512) && (value.active() || value.groupId() == null); }
    private static boolean ids(java.util.Collection<String> values) { return values != null && values.size() <= 4096 && values.stream().allMatch(value -> text(value, 512)); }
    public interface ActorQuery { String userId(); }
    public record Query(Scope scope, String userId, String bucket, Integer limit, String cursor, String workspace) implements ActorQuery {}
    public record UnreadQuery(Scope scope, String userId) implements ActorQuery {}
    public static boolean valid(UnreadQuery q) { return q != null && scope(q.scope()) && q.userId() != null && text(q.userId(), 512); }
    public static boolean valid(Query q) { return q != null && scope(q.scope()) && q.userId()!=null && text(q.userId(),512) && text(q.bucket(),128) && text(q.cursor(),8192) && text(q.workspace(),512); }
}
