package com.raxim.myscoutee.microservices.transport.email;

import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.domain.email.api.EmailLinks;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reads only the public link origin, never the email provider settings or credentials. */
public final class RemoteEmailLinks extends RemoteReadTransport implements EmailLinks {
    public RemoteEmailLinks(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
    @Override public String getBaseUrl() {
        return exchange(EmailLinksProtocol.PATH, new TypeReference<EmailLinksProtocol.Result>() {},
                "email.base-url", 16384, value -> Boolean.TRUE.equals(value.resolved())).baseUrl();
    }
}
