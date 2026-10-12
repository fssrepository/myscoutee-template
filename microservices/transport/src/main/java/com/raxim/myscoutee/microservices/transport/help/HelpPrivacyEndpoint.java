package com.raxim.myscoutee.microservices.transport.help;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.help.api.HelpPrivacy;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class HelpPrivacyEndpoint {
    private final HelpPrivacy owner;
    private final ObjectMapper mapper;
    public HelpPrivacyEndpoint(HelpPrivacy owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = HelpPrivacyProtocol.ANONYMIZE_AUTHORS, produces = "application/json")
    public ResponseEntity<?> anonymizeAuthors(HttpServletRequest request) throws java.io.IOException {
        return read(request, HelpPrivacyProtocol.UserCommand.class, HelpPrivacyProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(),
                        () -> { owner.anonymizeAuthors(command.userId()); return new HelpPrivacyProtocol.Receipt(true); }));
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, HelpPrivacyProtocol.MAX_REQUEST_BYTES);
            T query;
            try {
                query = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new HelpPrivacyProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new HelpPrivacyProtocol.Error("PURGE_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
