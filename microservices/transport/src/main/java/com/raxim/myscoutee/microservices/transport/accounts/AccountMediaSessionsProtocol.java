package com.raxim.myscoutee.microservices.transport.accounts;

public final class AccountMediaSessionsProtocol {
    public static final String ACCEPTS = "/_internal/v1/accounts/media-sessions/accepts";
    public static final String CURRENT = "/_internal/v1/accounts/media-sessions/current";
    public static final String REPLACE = "/_internal/v1/accounts/media-sessions/replace";
    public static final String AUDIENCE = "accounts:media-sessions";
    public static final int MAX_REQUEST_BYTES = 16384, MAX_RESPONSE_BYTES = 16384;
    private AccountMediaSessionsProtocol() {}
    public static String[] paths() { return new String[]{ACCEPTS, CURRENT, REPLACE}; }
    public record Check(String userId, String tokenHash) {}
    public record Session(String userId, String sessionId) {}
    public record Replacement(String userId, String sessionId, String tokenHash) {}
    public record Decision(Boolean value) {}
    public record Token(Boolean found, String hash) {}
    public record Error(String code) {}
    private static boolean id(String value) { return value != null && !value.isBlank() && value.length() <= 512; }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    public static boolean valid(Check value) { return value != null && id(value.userId()) && hash(value.tokenHash()); }
    public static boolean valid(Session value) { return value != null && id(value.userId()) && id(value.sessionId()); }
    public static boolean valid(Replacement value) { return value != null && id(value.userId()) && id(value.sessionId()) && hash(value.tokenHash()); }
    public static boolean valid(Token value) {
        return value != null && value.found() != null && (value.hash() == null || hash(value.hash()))
                && (value.found() || value.hash() == null);
    }
}
