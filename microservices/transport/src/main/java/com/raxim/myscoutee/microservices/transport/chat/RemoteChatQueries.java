package com.raxim.myscoutee.microservices.transport.chat;

import com.raxim.myscoutee.domain.chat.api.ChatQueries;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Bounded scoped call to the existing owner; no automatic retries. */
public final class RemoteChatQueries extends RemoteReadTransport implements ChatQueries {
    private final ObjectMapper mapper;
    private final com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Signer signer;
    public RemoteChatQueries(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics, com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Signer signer) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper; this.signer = signer;
    }
    @Override public com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDto queryChatById(String chatId, String requestedUserId) {
        var command = new ChatQueriesProtocol.QueryChatById1(ChatQueriesProtocol.Scope.current(), chatId, requestedUserId);
        if (!ChatQueriesProtocol.valid(command)) throw new IllegalArgumentException("Invalid chat-queries request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > ChatQueriesProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("chat-queries request exceeds byte limit");
        var result = ownerExchange(ChatQueriesProtocol.QUERYCHATBYID_1, bytes, new TypeReference<ChatQueriesProtocol.QueryChatById1Result>() {},
                "chat-queries.querychatbyid_1", ChatQueriesProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.resolved()));
        return result.value();
    }
    @Override public com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatPageResponseDto queryChatPage(String requestedUserId, String contextFilterValue, String sortValue, String sortDirectionValue, String secondaryFilterValue, String cursor, Integer limitValue, String rangeStartIso, String rangeEndIso, Boolean adminServiceOnly, String supportCaseFilterValue) {
        var command = new ChatQueriesProtocol.QueryChatPage1(ChatQueriesProtocol.Scope.current(), requestedUserId, contextFilterValue, sortValue, sortDirectionValue, secondaryFilterValue, cursor, limitValue, rangeStartIso, rangeEndIso, adminServiceOnly, supportCaseFilterValue);
        if (!ChatQueriesProtocol.valid(command)) throw new IllegalArgumentException("Invalid chat-queries request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > ChatQueriesProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("chat-queries request exceeds byte limit");
        var result = ownerExchange(ChatQueriesProtocol.QUERYCHATPAGE_1, bytes, new TypeReference<ChatQueriesProtocol.QueryChatPage1Result>() {},
                "chat-queries.querychatpage_1", ChatQueriesProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.resolved()));
        return result.value();
    }
    @Override public com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessagesPageResponseDto queryMessagesPage(String chatId, String requestedUserId, Integer limit, String cursor) {
        var command = new ChatQueriesProtocol.QueryMessagesPage1(ChatQueriesProtocol.Scope.current(), chatId, requestedUserId, limit, cursor);
        if (!ChatQueriesProtocol.valid(command)) throw new IllegalArgumentException("Invalid chat-queries request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > ChatQueriesProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("chat-queries request exceeds byte limit");
        var result = ownerExchange(ChatQueriesProtocol.QUERYMESSAGESPAGE_1, bytes, new TypeReference<ChatQueriesProtocol.QueryMessagesPage1Result>() {},
                "chat-queries.querymessagespage_1", ChatQueriesProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.resolved()));
        return result.value();
    }
    @Override public com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessagesPageResponseDto queryMessagesPage(String chatId, String requestedUserId, Integer limit, String cursor, String targetMessageId) {
        var command = new ChatQueriesProtocol.QueryMessagesPage2(ChatQueriesProtocol.Scope.current(), chatId, requestedUserId, limit, cursor, targetMessageId);
        if (!ChatQueriesProtocol.valid(command)) throw new IllegalArgumentException("Invalid chat-queries request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > ChatQueriesProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("chat-queries request exceeds byte limit");
        var result = ownerExchange(ChatQueriesProtocol.QUERYMESSAGESPAGE_2, bytes, new TypeReference<ChatQueriesProtocol.QueryMessagesPage2Result>() {},
                "chat-queries.querymessagespage_2", ChatQueriesProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.resolved()));
        return result.value();
    }
    @Override public com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessagesPageResponseDto queryMessagesPage(String chatId, String requestedUserId, Integer limit, String cursor, String targetMessageId, boolean markViewed) {
        var command = new ChatQueriesProtocol.QueryMessagesPage3(ChatQueriesProtocol.Scope.current(), chatId, requestedUserId, limit, cursor, targetMessageId, markViewed);
        if (!ChatQueriesProtocol.valid(command)) throw new IllegalArgumentException("Invalid chat-queries request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > ChatQueriesProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("chat-queries request exceeds byte limit");
        var result = ownerExchange(ChatQueriesProtocol.QUERYMESSAGESPAGE_3, bytes, new TypeReference<ChatQueriesProtocol.QueryMessagesPage3Result>() {},
                "chat-queries.querymessagespage_3", ChatQueriesProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.resolved()));
        return result.value();
    }
    private <T> T ownerExchange(String path, byte[] bytes, TypeReference<T> type, String operation, int limit,
            java.util.function.Predicate<T> valid) {
        try {
            if (ChatQueriesProtocol.delegated(path)) return exchangeAuthorized(path, bytes, type, operation, limit, valid,
                    java.util.Map.of(com.raxim.myscoutee.microservices.transport.security.ActorDelegation.HEADER,
                            signer.issueForCurrent(ChatQueriesProtocol.AUDIENCE, "POST", path, bytes)));
            return exchange(path, bytes, type, operation, limit, valid);
        }
        catch (RemoteReadException failure) {
            if (failure.status() >= 400 && failure.status() < 500 && "OWNER_REJECTED".equals(failure.remoteCode()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(failure.status()));
            throw failure;
        }
    }

}
