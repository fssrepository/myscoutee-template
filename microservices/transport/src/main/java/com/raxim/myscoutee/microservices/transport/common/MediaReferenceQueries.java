package com.raxim.myscoutee.microservices.transport.common;

import java.io.IOException;
import java.util.List;
import com.raxim.myscoutee.common.dto.MediaUrlsQueryDto;
import jakarta.servlet.http.HttpServletRequest;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies.RejectedQuery;
import tools.jackson.databind.ObjectMapper;

/** Shared wire bounds for the existing media policy's URL-variant queries. */
public final class MediaReferenceQueries {
    public static final int MAX_QUERY_BYTES = 1024 * 1024;
    private MediaReferenceQueries() {}

    private static boolean validUrls(List<String> urls) {
        // MediaPolicyFacts produces at most 20 variants of one <=2048-character key.
        return urls != null && !urls.isEmpty() && urls.size() <= 32
                && urls.stream().allMatch(url -> url != null && !url.isBlank() && url.length() <= 24576);
    }

    public static List<String> read(HttpServletRequest request, ObjectMapper mapper) throws IOException {
        var query = ReadQueryBodies.read(request, mapper, MAX_QUERY_BYTES);
        List<String> urls = new java.util.ArrayList<>();
        if (query == null || !query.isObject() || query.size() != 1
                || !query.has("urls") || !query.get("urls").isArray())
            throw new RejectedQuery(400, "INVALID_REQUEST");
        for (var value : query.get("urls")) {
            if (!value.isString()) throw new RejectedQuery(400, "INVALID_REQUEST");
            urls.add(value.asString());
        }
        if (!validUrls(urls)) throw new RejectedQuery(400, "INVALID_REQUEST");
        return urls;
    }

    public static byte[] encode(List<String> urls, ObjectMapper mapper) {
        if (!validUrls(urls)) throw new IllegalArgumentException("Invalid media URL query.");
        var body = mapper.writeValueAsBytes(new MediaUrlsQueryDto(urls));
        if (body.length > MAX_QUERY_BYTES) throw new IllegalArgumentException("Media URL query exceeds size limit.");
        return body;
    }

}
