package com.raxim.myscoutee.microservices.transport.notifications;

import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class NotificationDeliveryEndpoint {
    private final NotificationDelivery owner;
    private final ObjectMapper mapper;
    public NotificationDeliveryEndpoint(NotificationDelivery owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = {NotificationDeliveryProtocol.ENQUEUE, NotificationDeliveryProtocol.ONCE}, produces = "application/json")
    public ResponseEntity<?> enqueue(HttpServletRequest request) throws java.io.IOException {
        try {
            byte[] body = ReadQueryBodies.readBytes(request, NotificationDeliveryProtocol.MAX_REQUEST_BYTES);
            NotificationDeliveryProtocol.Command command;
            try {
                command = mapper.readerFor(NotificationDeliveryProtocol.Command.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readValue(body);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            boolean once = NotificationDeliveryProtocol.ONCE.equals(request.getRequestURI());
            if (!NotificationDeliveryProtocol.valid(command) || !once && command.operationId() != null)
                throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            var result = JobGroupContext.call(command.context().active(), command.context().groupId(), () -> once
                    ? owner.enqueueOnce(command.operationId(), command.intent()) : owner.enqueue(command.intent()));
            var receipt = result.map(item -> new NotificationDeliveryProtocol.ReceiptDto(item.getId(), item.getSyncKey())).orElse(null);
            return response(200, new NotificationDeliveryProtocol.Result(true, receipt));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new NotificationDeliveryProtocol.Error(invalid.code())); }
        catch (RuntimeException failed) { return response(503, new NotificationDeliveryProtocol.Error("ENQUEUE_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
