package com.raxim.myscoutee.microservices.transport.chat;

public final class ChatMediaVisibilityProtocol {
    public static final String PATH = "/_internal/v1/chat/media-visibility";
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private ChatMediaVisibilityProtocol() {}
    public record Error(String code) {}
}
