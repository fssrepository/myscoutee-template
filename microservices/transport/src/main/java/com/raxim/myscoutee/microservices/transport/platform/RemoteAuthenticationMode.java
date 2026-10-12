package com.raxim.myscoutee.microservices.transport.platform;

import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.domain.platform.api.AuthenticationMode;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reads current owner state; a transport failure is never replaced with a deployment default. */
public final class RemoteAuthenticationMode extends RemoteReadTransport implements AuthenticationMode {
    public RemoteAuthenticationMode(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
    @Override public boolean isFirebaseEnabled() {
        return exchange(AuthenticationModeProtocol.PATH, new TypeReference<Boolean>() {},
                "authentication-mode.read", AuthenticationModeProtocol.MAX_RESPONSE_BYTES, value -> true);
    }
}
