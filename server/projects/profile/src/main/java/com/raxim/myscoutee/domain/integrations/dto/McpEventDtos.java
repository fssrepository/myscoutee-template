package com.raxim.myscoutee.domain.integrations.dto;

import java.util.Map;

public final class McpEventDtos {
    private McpEventDtos() { }
    public record Request(String name, Map<String, Object> arguments, Delivery delivery, Long ttlMs, String cursor) { }
    public record Delivery(String mode, String url, String secret) {
        @Override public String toString() { return "Delivery[mode=" + mode + ", credentials=redacted]"; }
    }
    public record Subscribed(String id, String refreshBefore, String cursor, boolean truncated) { }
    /** Sanitized protocol errors cross the internal HTTP adapter without echoing requests. */
    public record Failure(int code, String message, Map<String, String> data) { }
}
