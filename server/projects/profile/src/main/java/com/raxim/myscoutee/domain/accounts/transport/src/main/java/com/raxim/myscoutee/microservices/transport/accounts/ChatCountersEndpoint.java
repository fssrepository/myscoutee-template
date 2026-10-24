package com.raxim.myscoutee.microservices.transport.accounts;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.accounts.api.ChatCounters;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class ChatCountersEndpoint {
    private final ChatCounters owner;
    private final ObjectMapper mapper;
    public ChatCountersEndpoint(ChatCounters owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = ChatCountersProtocol.SINGLE, produces = "application/json")
    public ResponseEntity<?> single(HttpServletRequest request) throws java.io.IOException {
        return read(request, ChatCountersProtocol.Single.class, ChatCountersProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> receipt(() -> owner.increment(command.change().change()))));
    }
    @PostMapping(value = ChatCountersProtocol.BATCH, produces = "application/json")
    public ResponseEntity<?> batch(HttpServletRequest request) throws java.io.IOException {
        return read(request, ChatCountersProtocol.Batch.class, ChatCountersProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> receipt(() -> owner.increment(command.changes().stream().map(ChatCountersProtocol.Delta::change).toList()))));
    }
    private ChatCountersProtocol.Receipt receipt(Runnable action) { action.run(); return new ChatCountersProtocol.Receipt(true); }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, ChatCountersProtocol.MAX_REQUEST_BYTES);
            T query = ReadQueryBodies.decode(bytes, mapper, type, valid, true);
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new ChatCountersProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new ChatCountersProtocol.Error("CHAT_COUNTERS_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
