package com.raxim.myscoutee.microservices.transport.chat;

public final class ChatDirectoryProtocol {
    public static final String ENTRY = "/_internal/v1/chat/directory/entry";
    public static final String ENTRIES = "/_internal/v1/chat/directory/entries";
    public static final String UNREAD = "/_internal/v1/chat/directory/unread-version";
    public static final String COUNTS = "/_internal/v1/chat/directory/support-counts";
    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private ChatDirectoryProtocol() {}
    public record Error(String code) {}
    public static String[] paths() { return new String[]{ENTRY, ENTRIES, UNREAD, COUNTS}; }
}
