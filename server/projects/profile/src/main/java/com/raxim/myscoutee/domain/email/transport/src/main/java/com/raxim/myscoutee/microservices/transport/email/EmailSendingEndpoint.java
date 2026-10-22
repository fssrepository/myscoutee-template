package com.raxim.myscoutee.microservices.transport.email;

import com.raxim.myscoutee.microservices.transport.common.ProtocolFields;
import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.email.api.EmailSending;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class EmailSendingEndpoint {
    private final EmailSending owner;
    private final ObjectMapper mapper;
    public EmailSendingEndpoint(EmailSending owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = EmailSendingProtocol.ENABLED, produces = "application/json")
    public ResponseEntity<?> enabled(HttpServletRequest request) throws java.io.IOException {
        return read(request, EmailSendingProtocol.Query.class, EmailSendingProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new EmailSendingProtocol.Decision(owner.enabled())));
    }
    @PostMapping(value = EmailSendingProtocol.SEND, produces = "application/json")
    public ResponseEntity<?> send(HttpServletRequest request) throws java.io.IOException {
        return read(request, EmailSendingProtocol.Send.class, EmailSendingProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> owner.send(command.content())));
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        return new ReadQueryBodies.CommandReader(EmailSendingProtocol.MAX_REQUEST_BYTES, "EMAIL_SENDING_UNAVAILABLE")
                .read(request, mapper, type, valid, action);
    }

}
