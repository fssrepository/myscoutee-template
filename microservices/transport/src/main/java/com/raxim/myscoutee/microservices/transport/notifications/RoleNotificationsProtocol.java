package com.raxim.myscoutee.microservices.transport.notifications;


public final class RoleNotificationsProtocol {
    public static final String RELEASE = "/_internal/v1/notifications/roles/release";
    public static final int MAX_REQUEST_BYTES = 8192;
    private RoleNotificationsProtocol() {}
    public record Release(String version) {}
    public record Receipt(Boolean completed) {}
    public record Error(String code) {}
    public static String[] paths() { return new String[]{RELEASE}; }
    public static boolean bounded(String value) { return value == null || value.length() <= 512; }


}
