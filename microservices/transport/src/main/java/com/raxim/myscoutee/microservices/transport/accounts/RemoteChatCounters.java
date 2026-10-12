package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.ChatCounters;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Existing native worker operations, single attempt only; workflow acknowledgment recovery is separate. */
public final class RemoteChatCounters extends RemoteReadTransport implements ChatCounters {
    private final ObjectMapper mapper;
    public RemoteChatCounters(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public void increment(Change change) {
        var command = new ChatCountersProtocol.Single(ChatCountersProtocol.Scope.current(), ChatCountersProtocol.Delta.from(change));
        if (!ChatCountersProtocol.valid(command)) throw new IllegalArgumentException("Invalid chat-counters request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > ChatCountersProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("chat-counters request exceeds byte limit");
        var result = exchange(ChatCountersProtocol.SINGLE, bytes, new TypeReference<ChatCountersProtocol.Receipt>() {},
                "chat-counters.single", ChatCountersProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.completed()));
    }
    @Override public void increment(java.util.Collection<Change> changes) {
        var command = new ChatCountersProtocol.Batch(ChatCountersProtocol.Scope.current(), changes.stream().map(ChatCountersProtocol.Delta::from).toList());
        if (!ChatCountersProtocol.valid(command)) throw new IllegalArgumentException("Invalid chat-counters request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > ChatCountersProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("chat-counters request exceeds byte limit");
        var result = exchange(ChatCountersProtocol.BATCH, bytes, new TypeReference<ChatCountersProtocol.Receipt>() {},
                "chat-counters.batch", ChatCountersProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.completed()));
    }
}
