package com.raxim.myscoutee.microservices.transport.email;

import com.raxim.myscoutee.microservices.transport.common.ProtocolFields;
import com.raxim.myscoutee.domain.email.api.EmailSending;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Bounded scoped call to the existing owner; no automatic retries. */
public final class RemoteEmailSending extends RemoteReadTransport implements EmailSending {
    private final ObjectMapper mapper;
    public RemoteEmailSending(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public boolean enabled() {
        var command = new EmailSendingProtocol.Query(ProtocolFields.JobScope.current());
        if (!EmailSendingProtocol.valid(command)) throw new IllegalArgumentException("Invalid email-sending request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > EmailSendingProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("email-sending request exceeds byte limit");
        var result = ownerExchange(EmailSendingProtocol.ENABLED, bytes, new TypeReference<EmailSendingProtocol.Decision>() {},
                "email-sending.enabled", EmailSendingProtocol.MAX_RESPONSE_BYTES, v -> v.value()!=null);
        return result.value();
    }
    @Override public com.raxim.myscoutee.domain.email.api.EmailSendResult send(com.raxim.myscoutee.domain.email.api.EmailSendRequest content) {
        var command = new EmailSendingProtocol.Send(ProtocolFields.JobScope.current(), content);
        if (!EmailSendingProtocol.valid(command)) throw new IllegalArgumentException("Invalid email-sending request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > EmailSendingProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("email-sending request exceeds byte limit");
        var result = ownerExchange(EmailSendingProtocol.SEND, bytes, new TypeReference<com.raxim.myscoutee.domain.email.api.EmailSendResult>() {},
                "email-sending.send", EmailSendingProtocol.MAX_RESPONSE_BYTES, v -> v.retryAfterSeconds()>=0 && !(v.accepted() && (v.outcomeUnknown() || v.retryable() || v.suppressed())));
        return result;
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
