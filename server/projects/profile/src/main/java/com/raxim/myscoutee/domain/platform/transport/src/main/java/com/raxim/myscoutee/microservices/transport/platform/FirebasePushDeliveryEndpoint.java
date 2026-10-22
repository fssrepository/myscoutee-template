package com.raxim.myscoutee.microservices.transport.platform;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class FirebasePushDeliveryEndpoint {
    private final FirebasePushDelivery owner;
    private final ObjectMapper mapper;
    public FirebasePushDeliveryEndpoint(FirebasePushDelivery owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = FirebasePushDeliveryProtocol.SINGLE, produces = "application/json")
    public ResponseEntity<?> single(HttpServletRequest request) throws java.io.IOException {
        return read(request, FirebasePushDeliveryProtocol.Single.class, FirebasePushDeliveryProtocol::valid,
                command -> { owner.send(command.message()); return new FirebasePushDeliveryProtocol.Receipt(true); });
    }
    @PostMapping(value = FirebasePushDeliveryProtocol.BATCH, produces = "application/json")
    public ResponseEntity<?> batch(HttpServletRequest request) throws java.io.IOException {
        return read(request, FirebasePushDeliveryProtocol.Batch.class, FirebasePushDeliveryProtocol::valid,
                command -> owner.sendAll(command.messages(), command.tokens()));
    }
    @PostMapping(value = FirebasePushDeliveryProtocol.MULTICAST, produces = "application/json")
    public ResponseEntity<?> multicast(HttpServletRequest request) throws java.io.IOException {
        return read(request, FirebasePushDeliveryProtocol.Multicast.class, FirebasePushDeliveryProtocol::valid,
                command -> owner.sendMulticast(command.message(), command.tokens()));
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, FirebasePushDeliveryProtocol.MAX_REQUEST_BYTES);
            T query = ReadQueryBodies.decode(bytes, mapper, type, valid, true);
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new FirebasePushDeliveryProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new FirebasePushDeliveryProtocol.Error("FIREBASE_PUSH_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
