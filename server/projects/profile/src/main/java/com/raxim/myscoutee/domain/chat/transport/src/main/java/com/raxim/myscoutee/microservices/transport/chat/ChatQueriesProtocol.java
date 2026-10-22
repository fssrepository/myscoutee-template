package com.raxim.myscoutee.microservices.transport.chat;

import static com.raxim.myscoutee.microservices.transport.common.ProtocolFields.text;
import static com.raxim.myscoutee.microservices.transport.common.ProtocolFields.ids;

import com.raxim.myscoutee.domain.chat.api.ChatQueries;

public final class ChatQueriesProtocol {
    public static final String QUERYCHATBYID_1 = "/_internal/v1/chat/chat-queries/querychatbyid_1";
    public static final String QUERYCHATPAGE_1 = "/_internal/v1/chat/chat-queries/querychatpage_1";
    public static final String QUERYMESSAGESPAGE_1 = "/_internal/v1/chat/chat-queries/querymessagespage_1";
    public static final String QUERYMESSAGESPAGE_2 = "/_internal/v1/chat/chat-queries/querymessagespage_2";
    public static final String QUERYMESSAGESPAGE_3 = "/_internal/v1/chat/chat-queries/querymessagespage_3";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    public static final String AUDIENCE = "chat:chat-queries";
    public static boolean delegated(String path) { return java.util.Set.of(QUERYCHATBYID_1, QUERYCHATPAGE_1, QUERYMESSAGESPAGE_1, QUERYMESSAGESPAGE_2, QUERYMESSAGESPAGE_3).contains(path); }
    private ChatQueriesProtocol() {}
    public static String[] paths() { return new String[]{QUERYCHATBYID_1, QUERYCHATPAGE_1, QUERYMESSAGESPAGE_1, QUERYMESSAGESPAGE_2, QUERYMESSAGESPAGE_3}; }
    public record Error(String code) {}
    public record Scope(Boolean active, String groupId) {
        public static Scope current() { return new Scope(com.raxim.myscoutee.domain.platform.api.JobGroupContext.isActive(), com.raxim.myscoutee.domain.platform.api.JobGroupContext.groupId()); }
    }
    private static boolean scope(Scope value) { return value != null && value.active() != null && text(value.groupId(), 512) && (value.active() || value.groupId() == null); }
    public record Receipt(Boolean completed) {}
    public record QueryChatById1(Scope scope, String chatId, String requestedUserId) {}
    public static boolean valid(QueryChatById1 q) { return q!=null && scope(q.scope()) && text(q.chatId(),8192) && text(q.requestedUserId(),8192); }
    public record QueryChatById1Result(Boolean resolved, com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDto value) {}
    public record QueryChatPage1(Scope scope, String requestedUserId, String contextFilterValue, String sortValue, String sortDirectionValue, String secondaryFilterValue, String cursor, Integer limitValue, String rangeStartIso, String rangeEndIso, Boolean adminServiceOnly, String supportCaseFilterValue) {}
    public static boolean valid(QueryChatPage1 q) { return q!=null && scope(q.scope()) && text(q.requestedUserId(),8192) && text(q.contextFilterValue(),8192) && text(q.sortValue(),8192) && text(q.sortDirectionValue(),8192) && text(q.secondaryFilterValue(),8192) && text(q.cursor(),8192) && text(q.rangeStartIso(),8192) && text(q.rangeEndIso(),8192) && text(q.supportCaseFilterValue(),8192); }
    public record QueryChatPage1Result(Boolean resolved, com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatPageResponseDto value) {}
    public record QueryMessagesPage1(Scope scope, String chatId, String requestedUserId, Integer limit, String cursor) {}
    public static boolean valid(QueryMessagesPage1 q) { return q!=null && scope(q.scope()) && text(q.chatId(),8192) && text(q.requestedUserId(),8192) && text(q.cursor(),8192); }
    public record QueryMessagesPage1Result(Boolean resolved, com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessagesPageResponseDto value) {}
    public record QueryMessagesPage2(Scope scope, String chatId, String requestedUserId, Integer limit, String cursor, String targetMessageId) {}
    public static boolean valid(QueryMessagesPage2 q) { return q!=null && scope(q.scope()) && text(q.chatId(),8192) && text(q.requestedUserId(),8192) && text(q.cursor(),8192) && text(q.targetMessageId(),8192); }
    public record QueryMessagesPage2Result(Boolean resolved, com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessagesPageResponseDto value) {}
    public record QueryMessagesPage3(Scope scope, String chatId, String requestedUserId, Integer limit, String cursor, String targetMessageId, @com.fasterxml.jackson.annotation.JsonProperty(required = true) @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL) boolean markViewed) {}
    public static boolean valid(QueryMessagesPage3 q) { return q!=null && scope(q.scope()) && text(q.chatId(),8192) && text(q.requestedUserId(),8192) && text(q.cursor(),8192) && text(q.targetMessageId(),8192); }
    public record QueryMessagesPage3Result(Boolean resolved, com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessagesPageResponseDto value) {}
}
