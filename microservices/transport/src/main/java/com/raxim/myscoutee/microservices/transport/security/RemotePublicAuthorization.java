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
        return authorize(snapshot(source), nonce);
    }
    /** Bounded immutable authentication input; never retain a recycled servlet request. */
    public record Request(String method, String target, String peer, java.util.Map<String, java.util.List<String>> headers) {
        @Override public String toString() { return "PublicAuthorizationRequest[redacted]"; }
    }
    public static Request snapshot(HttpServletRequest source) {
        var headers = new java.util.LinkedHashMap<String, java.util.List<String>>();
        int bytes = 0;
        for (String name : PublicAuthorizationProtocol.REQUEST_HEADERS) {
            var values = java.util.List.copyOf(Collections.list(source.getHeaders(name)));
            for (String value : values) {
                bytes += name.length() + value.length();
                if (bytes > 32768) throw new IllegalArgumentException("Authorization headers exceed limit");
            }
            headers.put(name, values);
        }
        return new Request(source.getMethod(), PublicAuthorizationProtocol.target(source),
                source.getRemoteAddr(), java.util.Map.copyOf(headers));
    }
    public HttpResponse<byte[]> authorize(Request source, String nonce) {
        var request = HttpRequest.newBuilder(origin.resolve(source.target())).timeout(timeout)
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .header(PublicAuthorizationProtocol.METHOD, source.method())
                .header(PublicAuthorizationProtocol.CLIENT, service)
                .header(PublicAuthorizationProtocol.TOKEN, token)
                .header(PublicAuthorizationProtocol.NONCE, nonce)
                .header(PublicAuthorizationProtocol.PEER, source.peer());
        source.headers().forEach((name, values) -> values.forEach(value -> request.header(name, value)));
        return exchangeRequest(request.build(), "public-authority.authorize", 32768, response -> response);
    }
}
