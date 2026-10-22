package com.raxim.myscoutee.microservices.transport.integrations;

import com.raxim.myscoutee.microservices.transport.common.ProtocolFields;
import com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Bounded scoped call to the existing owner; no automatic retries. */
public final class RemoteRoleAttentionEvents extends RemoteReadTransport implements RoleAttentionEvents {
    private final ObjectMapper mapper;
    public RemoteRoleAttentionEvents(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public void enqueueRoleAttention(RoleAttention notification) {
        var command = new RoleAttentionEventsProtocol.Command(ProtocolFields.JobScope.current(), notification);
        if (!RoleAttentionEventsProtocol.valid(command)) throw new IllegalArgumentException("Invalid role-attention-events request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > RoleAttentionEventsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("role-attention-events request exceeds byte limit");
        var result = ownerExchange(RoleAttentionEventsProtocol.ENQUEUE, bytes, new TypeReference<ProtocolFields.Receipt>() {},
                "role-attention-events.enqueue", RoleAttentionEventsProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.completed()));
    }
    private <T> T ownerExchange(String path, byte[] bytes, TypeReference<T> type, String operation, int limit,
            java.util.function.Predicate<T> valid) {
        try { return exchange(path, bytes, type, operation, limit, valid); }
        catch (RemoteReadException failure) {
            if (failure.status() >= 400 && failure.status() < 500 && "OWNER_REJECTED".equals(failure.remoteCode()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(failure.status()));
            throw failure;
        }
    }

}
