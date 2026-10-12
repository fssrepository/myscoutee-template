package com.raxim.myscoutee.microservices.transport.email;

public final class EmailLinksProtocol {
    public static final String PATH = "/_internal/v1/email/base-url";
    private EmailLinksProtocol() {}
    public record Result(Boolean resolved, String baseUrl) {}
    public record Error(String code) {}
}
