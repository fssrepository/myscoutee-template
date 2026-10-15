package com.raxim.myscoutee.microservices.transport.media;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import com.raxim.myscoutee.domain.media.api.MediaDeletionQueue;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Existing worker owns deletion and final job status; a lost reply is never retried here. */
public final class RemoteMediaDeletionQueue extends RemoteReadTransport implements MediaDeletionQueue {
    private final ObjectMapper mapper;
    public RemoteMediaDeletionQueue(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics, Duration.ofMinutes(15)); this.mapper = mapper;
    }
    @Override public void enqueue(String group, java.util.List<String> references) {
        var command = new MediaDeletionQueueProtocol.Enqueue(group, references);
        if (!MediaDeletionQueueProtocol.valid(command)) throw new IllegalArgumentException("Invalid media deletion request");
        exchange(MediaDeletionQueueProtocol.ENQUEUE, mapper.writeValueAsBytes(command),
                new TypeReference<MediaDeletionQueueProtocol.Receipt>() {}, "media.deletion.enqueue",
                MediaDeletionQueueProtocol.MAX_RESPONSE_BYTES, result -> Boolean.TRUE.equals(result.returned()));
    }
    @Override public void run(String actor, Instant startedAt) {
        var command = MediaDeletionQueueProtocol.Command.current(actor, startedAt);
        if (!MediaDeletionQueueProtocol.valid(command)) throw new IllegalArgumentException("Invalid media deletion request");
        exchange(MediaDeletionQueueProtocol.RUN, mapper.writeValueAsBytes(command),
                new TypeReference<MediaDeletionQueueProtocol.Receipt>() {}, "media.media-deletion",
                MediaDeletionQueueProtocol.MAX_RESPONSE_BYTES, result -> Boolean.TRUE.equals(result.returned()));
    }
}
