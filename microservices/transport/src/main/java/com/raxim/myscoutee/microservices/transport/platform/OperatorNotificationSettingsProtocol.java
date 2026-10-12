package com.raxim.myscoutee.microservices.transport.platform;

public final class OperatorNotificationSettingsProtocol {
    public static final String PATH = "/_internal/v1/platform/operator-notification-recipients";
    public static final int MAX_RESPONSE_BYTES = 16384;
    private OperatorNotificationSettingsProtocol() {}
    public record Error(String code) {}
}
