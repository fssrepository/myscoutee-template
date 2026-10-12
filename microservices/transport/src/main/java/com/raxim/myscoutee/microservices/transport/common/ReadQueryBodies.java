package com.raxim.myscoutee.microservices.transport.common;

import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared bounded JSON envelope; each explicit read contract validates its own fields. */
public final class ReadQueryBodies {
    private ReadQueryBodies() {}
    public static JsonNode read(HttpServletRequest request, ObjectMapper mapper, int maxBytes) throws IOException {
        var body = readBytes(request, maxBytes);
        try {
            return mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
        } catch (RuntimeException invalid) { throw new RejectedQuery(400, "INVALID_REQUEST"); }
    }
    /** Same input bounds for a signed command, whose signature covers the exact bytes. */
    public static byte[] readBytes(HttpServletRequest request, int maxBytes) throws IOException {
        if (!"application/json".equalsIgnoreCase(String.valueOf(request.getContentType()).split(";", 2)[0].trim()))
            throw new RejectedQuery(415, "INVALID_CONTENT_TYPE");
        if (request.getContentLengthLong() > maxBytes) throw new RejectedQuery(413, "QUERY_TOO_LARGE");
        var body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) throw new RejectedQuery(413, "QUERY_TOO_LARGE");
        return body;
    }
    public static final class RejectedQuery extends RuntimeException {
        private final int status;
        private final String code;
        public RejectedQuery(int status, String code) { super(code); this.status = status; this.code = code; }
        public int status() { return status; }
        public String code() { return code; }
    }
}
