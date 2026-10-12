package com.raxim.myscoutee.microservices.transport.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;

/** Private authentication exchange: a fresh receiver nonce, never the business request body. */
public final class PublicAuthorizationProtocol {
    public static final String CLIENT = "X-MyScoutee-Authorization-Client";
    public static final String TOKEN = "X-MyScoutee-Authorization-Token";
    public static final String NONCE = "X-MyScoutee-Authorization-Nonce";
    public static final String METHOD = "X-MyScoutee-Authorization-Method";
    public static final String PEER = "X-MyScoutee-Authorization-Peer";
    public static final String ATTRIBUTE = PublicAuthorizationProtocol.class.getName() + ".verified";
    public static final List<String> REQUEST_HEADERS = List.of("Authorization", "X-Authorization-Firebase",
            "X-Authorization-Link", "X-Demo-User-Id", "X-App-Session-Kind", "X-App-Session-Id",
            "Cookie", "User-Agent", "Origin", "X-Forwarded-For", "X-Forwarded-Proto", "X-Forwarded-Host");
    private PublicAuthorizationProtocol() {}
    public record Exchange(String client, String nonce) {}
    public record Result(String mode, String actor, String mediaUserId) {
        @Override public String toString() { return "PublicAuthorizationResult[redacted]"; }
    }
    public static String target(HttpServletRequest request) {
        String value = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        if (value.length() > 4096 || !value.startsWith("/") || value.startsWith("//")
                || value.contains("\\") || value.contains("#") || value.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IllegalArgumentException("Invalid public authorization target");
        return value;
    }
    public static String audience(String client) { return "assembly:public:" + client; }
    public static byte[] nonceBytes(String nonce) { return nonce.getBytes(StandardCharsets.US_ASCII); }
}
