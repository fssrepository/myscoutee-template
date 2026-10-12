package com.raxim.myscoutee.microservices.transport.media;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.media.api.MediaCleanup;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class MediaCleanupEndpoint {
    private final MediaCleanup owner;
    private final ObjectMapper mapper;
    public MediaCleanupEndpoint(MediaCleanup owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = MediaCleanupProtocol.DELETE_PUBLIC_OBJECT_OR_THROW, produces = "application/json")
    public ResponseEntity<?> deletePublicObjectOrThrow(HttpServletRequest request) throws java.io.IOException {
        return read(request, MediaCleanupProtocol.UserCommand.class, MediaCleanupProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(),
                        () -> { owner.deletePublicObjectOrThrow(command.reference()); return new MediaCleanupProtocol.Receipt(true); }));
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, MediaCleanupProtocol.MAX_REQUEST_BYTES);
            T query;
            try {
                query = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new MediaCleanupProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new MediaCleanupProtocol.Error("MEDIA_CLEANUP_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
