package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.ChatCounters;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

public final class ChatCountersProtocol {
    public static final String SINGLE = "/_internal/v1/accounts/chat-counters/single";
    public static final String BATCH = "/_internal/v1/accounts/chat-counters/batch";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private ChatCountersProtocol() {}
    public static String[] paths() { return new String[]{SINGLE, BATCH}; }
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(JobGroupContext.isActive(), JobGroupContext.groupId()); }
    }
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    private static boolean text(String value, int limit) { return value == null || value.length() <= limit; }
    private static boolean scope(Scope value) { return value != null && value.active() != null && text(value.groupId(), 512) && (value.active() || value.groupId() == null); }
    public record Delta(String ownerUserId, String context, Integer delta) {
        public static Delta from(ChatCounters.Change value) { return value == null ? null : new Delta(value.ownerUserId(), value.context(), value.delta()); }
        public ChatCounters.Change change() { return new ChatCounters.Change(ownerUserId, context, delta); }
    }
    public record Single(Scope scope, Delta change) {}
    public record Batch(Scope scope, java.util.List<Delta> changes) {}
    private static boolean delta(Delta value) {
        return value != null && value.ownerUserId() != null && !value.ownerUserId().isBlank() && text(value.ownerUserId(), 512) && value.delta() != null
                && (value.context() == null || java.util.Set.of("", "event", "subEvent", "group", "service", "contacts", "campaign", "cases", "groupSupport", "appSupport").contains(value.context()));
    }
    public static boolean valid(Single value) { return value != null && scope(value.scope()) && delta(value.change()); }
    public static boolean valid(Batch value) { return value != null && scope(value.scope()) && value.changes() != null && value.changes().size() <= 4096 && value.changes().stream().allMatch(ChatCountersProtocol::delta); }
}
