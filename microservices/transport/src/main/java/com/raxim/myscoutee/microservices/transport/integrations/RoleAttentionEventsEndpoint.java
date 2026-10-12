package com.raxim.myscoutee.microservices.transport.integrations;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class RoleAttentionEventsEndpoint {
    private final RoleAttentionEvents owner;
    private final ObjectMapper mapper;
    public RoleAttentionEventsEndpoint(RoleAttentionEvents owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = RoleAttentionEventsProtocol.ENQUEUE, produces = "application/json")
    public ResponseEntity<?> enqueue(HttpServletRequest request) throws java.io.IOException {
        return read(request, RoleAttentionEventsProtocol.Command.class, RoleAttentionEventsProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> receipt(() -> owner.enqueueRoleAttention(command.notification()))));
    }
    private RoleAttentionEventsProtocol.Receipt receipt(Runnable action) { action.run(); return new RoleAttentionEventsProtocol.Receipt(true); }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, RoleAttentionEventsProtocol.MAX_REQUEST_BYTES);
            T query;
            try {
                query = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new RoleAttentionEventsProtocol.Error(invalid.code())); }
        catch (org.springframework.web.server.ResponseStatusException rejected) {
            return response(rejected.getStatusCode().value(), new RoleAttentionEventsProtocol.Error("OWNER_REJECTED"));
        }
        catch (RuntimeException unavailable) { return response(503, new RoleAttentionEventsProtocol.Error("ROLE_ATTENTION_EVENTS_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
