package com.raxim.myscoutee.microservices.transport.notifications;

import com.raxim.myscoutee.domain.notifications.api.RoleNotifications;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class RoleNotificationsEndpoint {
    private final RoleNotifications owner;
    private final ObjectMapper mapper;
    public RoleNotificationsEndpoint(RoleNotifications owner,ObjectMapper mapper) {this.owner=owner;this.mapper=mapper;}
    @PostMapping(value=RoleNotificationsProtocol.RELEASE, produces="application/json")
    public ResponseEntity<?> release(HttpServletRequest request) throws java.io.IOException {
        try {
            var command=read(request,RoleNotificationsProtocol.Release.class);
            if(command.version()==null || !RoleNotificationsProtocol.bounded(command.version()))throw invalid();
            return publish(()->owner.releaseAvailable(command.version()));
        } catch(ReadQueryBodies.RejectedQuery invalid){return response(invalid.status(),new RoleNotificationsProtocol.Error(invalid.code()));}
    }


    private ResponseEntity<?> publish(Runnable action) {
        try {action.run();return response(200,new RoleNotificationsProtocol.Receipt(true));}
        catch(RuntimeException failed){return response(503,new RoleNotificationsProtocol.Error("PUBLICATION_OUTCOME_UNKNOWN"));}
    }
    private <T> T read(HttpServletRequest request,Class<T> type) throws java.io.IOException {
        var body=ReadQueryBodies.readBytes(request,RoleNotificationsProtocol.MAX_REQUEST_BYTES);
        try {
            T command=mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
            if(command==null)throw invalid();return command;
        } catch(RuntimeException invalid){throw invalid();}
    }
    private static ReadQueryBodies.RejectedQuery invalid(){return new ReadQueryBodies.RejectedQuery(400,"INVALID_REQUEST");}
    private ResponseEntity<?> response(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
}
