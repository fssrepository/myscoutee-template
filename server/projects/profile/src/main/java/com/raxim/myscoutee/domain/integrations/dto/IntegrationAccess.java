package com.raxim.myscoutee.domain.integrations.dto;

import java.util.Map;

/** Profile-owned access configuration, persisted as one revisioned value. */
public record IntegrationAccess(long revision, Mode mcp, Map<String, Mode> clients) {
    public enum Mode {
        BLOCKED, WRITE, READ, FULL;

        public boolean allowsRead() { return this == READ || this == FULL; }

        @com.fasterxml.jackson.annotation.JsonValue
        public String value() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static IntegrationAccess initial() {
        return new IntegrationAccess(0, Mode.BLOCKED, Map.of());
    }

    public Mode clientMode(String id) {
        return clients == null ? Mode.WRITE : clients.getOrDefault(id, Mode.WRITE);
    }

    public Mode mcpMode() { return mcp == null ? Mode.BLOCKED : mcp; }

    public Mode effective(String tokenId, boolean mcpClient) {
        Mode mode = clientMode(tokenId);
        return mcpClient && mcpMode().ordinal() < mode.ordinal() ? mcpMode() : mode;
    }
}
