package com.raxim.myscoutee.microservices.transport.administration;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class OperationalTelemetryEndpoint {
    private final OperationalTelemetry owner;
    private final ObjectMapper mapper;
    public OperationalTelemetryEndpoint(OperationalTelemetry owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = OperationalTelemetryProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> project(HttpServletRequest request) throws java.io.IOException {
        return read(request, OperationalTelemetryProtocol.Command.class, OperationalTelemetryProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(),
                        () -> { owner.record(command.eventKey(), command.domain(), command.entityId(), command.actorUserId(), command.amount(), command.tags()); return new OperationalTelemetryProtocol.Receipt(true); }));
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, OperationalTelemetryProtocol.MAX_REQUEST_BYTES);
            T query;
            try {
                query = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new OperationalTelemetryProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new OperationalTelemetryProtocol.Error("TELEMETRY_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
