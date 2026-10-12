package com.raxim.myscoutee.microservices.transport.chat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.raxim.myscoutee.domain.chat.api.ChatDirectory;
import com.raxim.myscoutee.domain.chat.dto.ChatDirectoryEntryDto;
import com.raxim.myscoutee.domain.chat.dto.ChatUnreadVersionDto;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class RemoteChatDirectory extends RemoteReadTransport implements ChatDirectory {
    public RemoteChatDirectory(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
    @Override public Optional<ChatDirectoryEntryDto> findByOwnerUserIdAndChatId(String ownerId, String chatId) {
        try {
            return Optional.of(exchange(queryPath(ChatDirectoryProtocol.ENTRY, "ownerId", ownerId, "chatId", chatId),
                    new TypeReference<ChatDirectoryEntryDto>() {}, "chat.directory-entry", ChatDirectoryProtocol.MAX_RESPONSE_BYTES, value -> true));
        } catch (RemoteReadException failure) {
            if (failure.status() == 404 && "NOT_FOUND".equals(failure.remoteCode())) return Optional.empty();
            throw failure;
        }
    }
    @Override public List<ChatDirectoryEntryDto> findByChatId(String chatId) {
        return exchange(queryPath(ChatDirectoryProtocol.ENTRIES, "chatId", chatId), new TypeReference<List<ChatDirectoryEntryDto>>() {},
                "chat.directory-entries", ChatDirectoryProtocol.MAX_RESPONSE_BYTES, value -> value.stream().allMatch(java.util.Objects::nonNull));
    }
    @Override public String unreadVersion(String owner) {
        return exchange(queryPath(ChatDirectoryProtocol.UNREAD, "ownerId", owner), new TypeReference<ChatUnreadVersionDto>() {},
                "chat.unread-version", 4096, value -> true).version();
    }
    @Override public Map<String, Integer> supportCaseCounts(String baseGroupId) {
        return exchange(queryPath(ChatDirectoryProtocol.COUNTS, "baseGroupId", baseGroupId), new TypeReference<Map<String, Integer>>() {},
                "chat.support-counts", 65536, value -> value.values().stream().allMatch(count -> count != null && count >= 0));
    }
}
