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
    /** Shared ids envelope: preserve null collections/elements and the existing 4096/512 bounds. */
    public static java.util.List<String> readIds(HttpServletRequest request, ObjectMapper mapper, int maxBytes) throws IOException {
        var query = read(request, mapper, maxBytes);
        if (query == null || !query.isObject() || query.size() != 1 || !query.has("ids"))
            throw new RejectedQuery(400, "INVALID_REQUEST");
        var ids = query.get("ids");
        if (ids.isNull()) return null;
        if (!ids.isArray() || ids.size() > 4096) throw new RejectedQuery(400, "INVALID_REQUEST");
        var result = new java.util.ArrayList<String>();
        for (var id : ids) {
            if (!id.isNull() && (!id.isString() || !ProtocolFields.text(id.asString(), 512)))
                throw new RejectedQuery(400, "INVALID_REQUEST");
            result.add(id.isNull() ? null : id.asString());
        }
        return result;
    }
    private record IdsQuery(java.util.List<String> ids) {}
    public static byte[] encodeIds(Iterable<String> ids, ObjectMapper mapper, int maxBytes,
            String invalidIds, String oversizedQuery) {
        java.util.List<String> bounded = null;
        if (ids != null) {
            bounded = new java.util.ArrayList<>();
            for (var id : ids) {
                if (bounded.size() == 4096 || !ProtocolFields.text(id, 512)) throw new IllegalArgumentException(invalidIds);
                bounded.add(id);
            }
        }
        var body = mapper.writeValueAsBytes(new IdsQuery(bounded));
        if (body.length > maxBytes) throw new IllegalArgumentException(oversizedQuery);
        return body;
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
    /** Decode an already bounded envelope, preserving each contract's primitive-null policy. */
    public static <T> T decode(byte[] body, ObjectMapper mapper, Class<T> type,
            java.util.function.Predicate<T> valid, boolean rejectNullPrimitives) {
        T query;
        try {
            var reader = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            if (rejectNullPrimitives) reader = reader.with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
            query = reader.readValue(body);
        } catch (RuntimeException invalid) { throw new RejectedQuery(400, "INVALID_REQUEST"); }
        if (!valid.test(query)) throw new RejectedQuery(400, "INVALID_REQUEST");
        return query;
    }
    public static final class RejectedQuery extends RuntimeException {
        private final int status;
        private final String code;
        public RejectedQuery(int status, String code) { super(code); this.status = status; this.code = code; }
        public int status() { return status; }
        public String code() { return code; }
    }

    /** Existing bounded GET lookup contract: owner-specific errors and no-store responses. */
    public static org.springframework.http.ResponseEntity<?> lookup(java.util.function.Supplier<?> read,
            java.util.function.Function<String, ?> error, String unavailableCode, String... parameters) {
        for (var value : parameters) if (!ProtocolFields.text(value, 512)) return response(400, error.apply("INVALID_REQUEST"));
        try {
            var result = read.get();
            return result == null ? response(404, error.apply("NOT_FOUND")) : response(200, result);
        } catch (RuntimeException unavailable) { return response(503, error.apply(unavailableCode)); }
    }
    private static org.springframework.http.ResponseEntity<?> response(int status, Object body) {
        return org.springframework.http.ResponseEntity.status(status)
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(body);
    }


    /** Per-owner bounds and delegation policy for the existing typed POST envelope. */
    public record CommandReader(int maxBytes, String unavailableCode) {
        public <T> org.springframework.http.ResponseEntity<?> read(HttpServletRequest request,
                ObjectMapper mapper, Class<T> type, java.util.function.Predicate<T> valid,
                java.util.function.Function<T, Object> action) throws IOException {
            try {
                var query = decode(readBytes(request, maxBytes), mapper, type, valid, true);
                return response(200, action.apply(query));
            } catch (RejectedQuery invalid) {
                return response(invalid.status(), new ProtocolFields.Error(invalid.code()));
            } catch (org.springframework.web.server.ResponseStatusException rejected) {
                return response(rejected.getStatusCode().value(), new ProtocolFields.Error("OWNER_REJECTED"));
            } catch (RuntimeException unavailable) {
                return response(503, new ProtocolFields.Error(unavailableCode));
            }
        }
    }

}
