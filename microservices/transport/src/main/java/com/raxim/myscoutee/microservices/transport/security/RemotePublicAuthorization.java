package com.raxim.myscoutee.microservices.transport.security;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Collections;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/** Authorization only. Never sends or consumes multipart/JSON/streaming business bodies. */
public final class RemotePublicAuthorization extends RemoteReadTransport {
    private final URI origin;
    private final String token;
    private final String service;
    private final Duration timeout;
    public RemotePublicAuthorization(URI origin, String token, boolean allowHttp, String service,
            Duration connectTimeout, Duration timeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, timeout, mapper, metrics);
        if (!service.matches("[a-z][a-z0-9-]{0,63}")) throw new IllegalArgumentException("Invalid service identity");
        this.origin = origin; this.token = token; this.service = service; this.timeout = timeout;
    }
    public HttpResponse<byte[]> authorize(HttpServletRequest source, String nonce) {
        String target = PublicAuthorizationProtocol.target(source);
        var request = HttpRequest.newBuilder(origin.resolve(target)).timeout(timeout)
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .header(PublicAuthorizationProtocol.METHOD, source.getMethod())
                .header(PublicAuthorizationProtocol.CLIENT, service)
                .header(PublicAuthorizationProtocol.TOKEN, token)
                .header(PublicAuthorizationProtocol.NONCE, nonce)
                .header(PublicAuthorizationProtocol.PEER, source.getRemoteAddr());
        int bytes = 0;
        for (String name : PublicAuthorizationProtocol.REQUEST_HEADERS) {
            var values = Collections.list(source.getHeaders(name));
            for (String value : values) {
                bytes += name.length() + value.length();
                if (bytes > 32768) throw new IllegalArgumentException("Authorization headers exceed limit");
                request.header(name, value);
            }
        }
        return exchangeRequest(request.build(), "public-authority.authorize", 32768, response -> response);
    }
}
