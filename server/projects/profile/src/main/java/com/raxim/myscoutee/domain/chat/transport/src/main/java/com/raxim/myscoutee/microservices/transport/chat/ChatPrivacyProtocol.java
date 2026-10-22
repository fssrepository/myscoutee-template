package com.raxim.myscoutee.microservices.transport.chat;

import java.util.*;
import com.raxim.myscoutee.microservices.transport.common.ProtocolFields;

public final class ChatPrivacyProtocol {
    public static final String PURGE_USER_CHAT_ACTIVITY = "/_internal/v1/chat/chat-privacy/purge-user-chat-activity";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private ChatPrivacyProtocol() {}
    public static String[] paths() { return new String[]{PURGE_USER_CHAT_ACTIVITY}; }
    private static boolean id(String value) { return ProtocolFields.requiredId(value); }
    private static boolean scope(ProtocolFields.JobScope value) {
        return value != null && ProtocolFields.jobScope(value.active(), value.groupId());
    }
    public static boolean valid(ProtocolFields.UserCommand value) { return ProtocolFields.validUserCommand(value); }
}
