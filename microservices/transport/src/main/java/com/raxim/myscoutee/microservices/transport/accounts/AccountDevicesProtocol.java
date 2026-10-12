package com.raxim.myscoutee.microservices.transport.accounts;

public final class AccountDevicesProtocol {
    public static final String ROOT = "/_internal/v1/accounts/devices";
    public static final String REGISTER = ROOT + "/register", REMOVE = ROOT + "/remove";
    public static final String AUDIENCE = "accounts:account-devices";
    public static final int MAX_REQUEST_BYTES = 32768, MAX_RESPONSE_BYTES = 16384;
    private AccountDevicesProtocol() {}
    public static String[] paths() { return new String[]{REGISTER, REMOVE}; }
    public record Register(String userId, String deviceId, String firebaseToken, String platform, Boolean enabled) {}
    public record Remove(String userId, String deviceId, String firebaseToken) {}
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    public static boolean valid(String userId, String deviceId, String token) {
        return userId != null && !userId.isBlank() && userId.length() <= 512
                && bounded(deviceId, 512) && bounded(token, 8192)
                && ((deviceId != null && !deviceId.isBlank()) || (token != null && !token.isBlank()));
    }
    public static boolean bounded(String value, int limit) { return value == null || value.length() <= limit; }
}
