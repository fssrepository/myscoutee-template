package com.raxim.myscoutee.microservices.transport.accounts;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.accounts.api.AccountConsents;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class AccountConsentsEndpoint {
    private final AccountConsents owner;
    private final ObjectMapper mapper;
    public AccountConsentsEndpoint(AccountConsents owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = AccountConsentsProtocol.REVISION, produces = "application/json")
    public ResponseEntity<?> revision(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountConsentsProtocol.Query.class, AccountConsentsProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountConsentsProtocol.Result(true, owner.forRevisionOrLatest(command.userId(), command.revisionId()).orElse(null))));
    }
    @PostMapping(value = AccountConsentsProtocol.LATEST, produces = "application/json")
    public ResponseEntity<?> latest(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountConsentsProtocol.Query.class, AccountConsentsProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountConsentsProtocol.Result(true, owner.latest(command.userId()).orElse(null))));
    }
    @PostMapping(value = AccountConsentsProtocol.RECORD, produces = "application/json")
    public ResponseEntity<?> record(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountConsentsProtocol.Save.class, AccountConsentsProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountConsentsProtocol.Result(true, owner.record(command.userId(), command.revisionId(), command.version(), command.optionalSections(), command.source()))));
    }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, AccountConsentsProtocol.MAX_REQUEST_BYTES);
            T query;
            try {
                query = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new AccountConsentsProtocol.Error(invalid.code())); }
        catch (org.springframework.web.server.ResponseStatusException rejected) {
            return response(rejected.getStatusCode().value(), new AccountConsentsProtocol.Error("OWNER_REJECTED"));
        }
        catch (RuntimeException unavailable) { return response(503, new AccountConsentsProtocol.Error("ACCOUNT_CONSENTS_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
