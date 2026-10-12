package com.raxim.myscoutee.microservices.transport.media;

import java.util.*;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.domain.media.api.MediaCleanup;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The existing account purge job owns retries and stage ordering; transport never retries. */
public final class RemoteMediaCleanup extends RemoteReadTransport implements MediaCleanup {
    private final ObjectMapper mapper;
    public RemoteMediaCleanup(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public void deletePublicObjectOrThrow(String reference) {
        var command = new MediaCleanupProtocol.UserCommand(MediaCleanupProtocol.Scope.current(), reference);
        send(MediaCleanupProtocol.DELETE_PUBLIC_OBJECT_OR_THROW, command, MediaCleanupProtocol.valid(command));
    }
    private void send(String path, Object command, boolean valid) {
        exchange(path, encode(command, valid), new TypeReference<MediaCleanupProtocol.Receipt>() {},
                "media-cleanup.command", MediaCleanupProtocol.MAX_RESPONSE_BYTES, value -> Boolean.TRUE.equals(value.completed()));
    }
    private byte[] encode(Object command, boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid media reference");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > MediaCleanupProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Media cleanup command exceeds byte limit");
        return bytes;
    }
}
