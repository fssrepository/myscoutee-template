package com.raxim.myscoutee.microservices.transport.localization;

/** Wire-only boundaries; the existing domain DTO remains the response contract. */
public final class LocalizationProtocol {
    public static final String PATH = "/_internal/v1/localization/bundle";
    public static final String MODE_HEADER = "X-MyScoutee-Session-Mode";
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private LocalizationProtocol() {}

    public record Error(String code) {}
}
