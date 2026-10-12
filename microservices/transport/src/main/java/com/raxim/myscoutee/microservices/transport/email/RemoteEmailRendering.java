package com.raxim.myscoutee.microservices.transport.email;

import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.domain.email.api.EmailRendering;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class RemoteEmailRendering extends RemoteReadTransport implements EmailRendering {
    private final ObjectMapper mapper;
    public RemoteEmailRendering(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public String renderText(String template, java.util.Map<String, String> variables) {
        var query = new EmailRenderingProtocol.Query(template, variables);
        if (!EmailRenderingProtocol.valid(query)) throw new IllegalArgumentException("Invalid email-rendering request");
        byte[] body = mapper.writeValueAsBytes(query);
        if (body.length > EmailRenderingProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Request exceeds limit");
        return exchange(EmailRenderingProtocol.PATH, body, new TypeReference<EmailRenderingProtocol.Result>() {},
                "email-rendering.read", 1048576, result -> Boolean.TRUE.equals(result.resolved()) && result.text() != null).text();
    }
}
