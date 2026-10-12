package com.raxim.myscoutee.microservices.transport.platform;

public final class AuthenticationModeProtocol {
    public static final String PATH = "/_internal/v1/platform/authentication-mode/firebase-enabled";
    public static final int MAX_RESPONSE_BYTES = 16384;
    private AuthenticationModeProtocol() {}
    public record Error(String code) {}
}
