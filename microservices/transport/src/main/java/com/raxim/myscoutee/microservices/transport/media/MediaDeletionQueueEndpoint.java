package com.raxim.myscoutee.microservices.transport.media;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import com.raxim.myscoutee.domain.media.api.MediaDeletionQueue;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class MediaDeletionQueueEndpoint {
    private final MediaDeletionQueue owner;
    private final ObjectMapper mapper;
    private final Semaphore execution = new Semaphore(1);
    public MediaDeletionQueueEndpoint(MediaDeletionQueue owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = MediaDeletionQueueProtocol.RUN, produces = "application/json")
    public ResponseEntity<?> run(HttpServletRequest request) throws IOException {
        try {
            var bytes = ReadQueryBodies.readBytes(request, MediaDeletionQueueProtocol.MAX_REQUEST_BYTES);
            MediaDeletionQueueProtocol.Command command;
            try {
                command = mapper.readerFor(MediaDeletionQueueProtocol.Command.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            } catch (RuntimeException error) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!MediaDeletionQueueProtocol.valid(command)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            if (!execution.tryAcquire()) return error(503, "BUSY");
            try {
                JobGroupContext.call(command.active(), command.groupId(), () -> { owner.run(command.actor(), command.startedAt()); return null; });
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MediaDeletionQueueProtocol.Receipt(true));
            } finally { execution.release(); }
        } catch (ReadQueryBodies.RejectedQuery error) { return error(error.status(), error.code()); }
        catch (RuntimeException error) { return error(503, "DELETION_OUTCOME_UNKNOWN"); }
    }
    @PostMapping(value = MediaDeletionQueueProtocol.ENQUEUE, produces = "application/json")
    public ResponseEntity<?> enqueue(HttpServletRequest request) throws IOException {
        try {
            var bytes = ReadQueryBodies.readBytes(request, MediaDeletionQueueProtocol.MAX_REQUEST_BYTES);
            MediaDeletionQueueProtocol.Enqueue command;
            try { command = mapper.readerFor(MediaDeletionQueueProtocol.Enqueue.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes); }
            catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!MediaDeletionQueueProtocol.valid(command)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            owner.enqueue(command.baseGroupId(), command.references());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MediaDeletionQueueProtocol.Receipt(true));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return error(invalid.status(), invalid.code()); }
        catch (IllegalArgumentException invalid) { return error(400, "INVALID_REQUEST"); }
        catch (RuntimeException unavailable) { return error(503, "DELETION_OUTCOME_UNKNOWN"); }
    }
    private ResponseEntity<?> error(int status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new MediaDeletionQueueProtocol.Error(code));
    }
}
