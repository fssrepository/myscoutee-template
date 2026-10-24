package com.raxim.myscoutee.microservices.transport.chat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import com.raxim.myscoutee.domain.chat.api.ChatMediaVisibility;
import com.raxim.myscoutee.domain.chat.dto.ChatMessageMediaVisibilityDto;
import com.raxim.myscoutee.microservices.transport.common.MediaReferenceQueries;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class RemoteChatMediaVisibility extends RemoteReadTransport implements ChatMediaVisibility {
    private final ObjectMapper mapper;
    public RemoteChatMediaVisibility(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
        this.mapper = mapper;
    }
    @Override public List<ChatMessageMediaVisibilityDto> messages(List<String> urls) {
        byte[] body = MediaReferenceQueries.encode(urls, mapper);
        return exchange(ChatMediaVisibilityProtocol.PATH, body, new TypeReference<List<ChatMessageMediaVisibilityDto>>() {},
                "chat.media-visibility", ChatMediaVisibilityProtocol.MAX_RESPONSE_BYTES,
                value -> value.stream().allMatch(java.util.Objects::nonNull));
    }
}
