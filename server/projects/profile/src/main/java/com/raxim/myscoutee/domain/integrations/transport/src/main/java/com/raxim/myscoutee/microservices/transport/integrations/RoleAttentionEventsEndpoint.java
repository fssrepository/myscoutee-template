package com.raxim.myscoutee.microservices.transport.integrations;

import com.raxim.myscoutee.microservices.transport.common.ProtocolFields;
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
    private ProtocolFields.Receipt receipt(Runnable action) { action.run(); return new ProtocolFields.Receipt(true); }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        return new ReadQueryBodies.CommandReader(RoleAttentionEventsProtocol.MAX_REQUEST_BYTES, "ROLE_ATTENTION_EVENTS_UNAVAILABLE")
                .read(request, mapper, type, valid, action);
    }

}
