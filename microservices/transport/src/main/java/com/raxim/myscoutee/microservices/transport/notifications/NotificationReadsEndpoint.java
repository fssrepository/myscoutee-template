package com.raxim.myscoutee.microservices.transport.notifications;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.notifications.api.NotificationReads;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class NotificationReadsEndpoint {
    private final NotificationReads owner;
    private final ObjectMapper mapper;
    private final com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Verifier verifier;
    public NotificationReadsEndpoint(NotificationReads owner, ObjectMapper mapper, com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Verifier verifier) { this.owner = owner; this.mapper = mapper; this.verifier = verifier; }
    @PostMapping(value = NotificationReadsProtocol.PAGE, produces = "application/json")
    public ResponseEntity<?> page(HttpServletRequest request) throws java.io.IOException {
        return read(request, NotificationReadsProtocol.PAGE, NotificationReadsProtocol.Query.class, NotificationReadsProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> owner.queryPage(command.userId(), command.bucket(), command.limit(), command.cursor(), command.workspace())));
    }
    @PostMapping(value = NotificationReadsProtocol.UNREAD, produces = "application/json")
    public ResponseEntity<?> unread(HttpServletRequest request) throws java.io.IOException {
        return read(request, NotificationReadsProtocol.UNREAD, NotificationReadsProtocol.UnreadQuery.class, NotificationReadsProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> owner.currentUnreadCount(command.userId())));
    }
    private <T extends NotificationReadsProtocol.ActorQuery> ResponseEntity<?> read(HttpServletRequest request, String path, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            var verified = com.raxim.myscoutee.microservices.transport.security.DelegatedRequestBodies.read(
                    request, mapper, verifier, NotificationReadsProtocol.AUDIENCE, path, type, NotificationReadsProtocol.MAX_REQUEST_BYTES);
            T query = verified.value();
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            if (!query.userId().equals(verified.principal().getUserId()))
                throw new org.springframework.security.access.AccessDeniedException("Actor mismatch");
            return verified.call(() -> response(200, action.apply(query)));
        } catch (org.springframework.security.access.AccessDeniedException denied) { return response(403, new NotificationReadsProtocol.Error("INVALID_DELEGATION"));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new NotificationReadsProtocol.Error(invalid.code())); }
        catch (org.springframework.web.server.ResponseStatusException rejected) {
            return response(rejected.getStatusCode().value(), new NotificationReadsProtocol.Error("OWNER_REJECTED"));
        }
        catch (RuntimeException unavailable) { return response(503, new NotificationReadsProtocol.Error("NOTIFICATION_READS_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
