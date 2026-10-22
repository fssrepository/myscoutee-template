package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.AccountMediaSessions;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import com.raxim.myscoutee.microservices.transport.security.DelegatedRequestBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class AccountMediaSessionsEndpoint {
    private final AccountMediaSessions owner;
    private final SelectedProfile profiles;
    private final ActorDelegation.Verifier verifier;
    private final ObjectMapper mapper;
    public AccountMediaSessionsEndpoint(AccountMediaSessions owner, SelectedProfile profiles, ActorDelegation.Verifier verifier, ObjectMapper mapper) {
        this.owner = owner; this.profiles = profiles; this.verifier = verifier; this.mapper = mapper;
    }
    @PostMapping(value = AccountMediaSessionsProtocol.ACCEPTS, produces = "application/json")
    public ResponseEntity<?> accepts(HttpServletRequest request) throws java.io.IOException {
        try {
            var bytes = ReadQueryBodies.readBytes(request, AccountMediaSessionsProtocol.MAX_REQUEST_BYTES);
            AccountMediaSessionsProtocol.Check command;
            try {
                command = mapper.readerFor(AccountMediaSessionsProtocol.Check.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!AccountMediaSessionsProtocol.valid(command)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, new AccountMediaSessionsProtocol.Authentication(owner.authenticateMediaToken(command.userId(), command.tokenHash()).orElse(null)));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new AccountMediaSessionsProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new AccountMediaSessionsProtocol.Error("MEDIA_SESSION_UNAVAILABLE")); }
    }
    @PostMapping(value = AccountMediaSessionsProtocol.CURRENT, produces = "application/json")
    public ResponseEntity<?> current(HttpServletRequest request) throws java.io.IOException {
        return signed(request, AccountMediaSessionsProtocol.CURRENT, AccountMediaSessionsProtocol.Session.class);
    }
    @PostMapping(value = AccountMediaSessionsProtocol.REPLACE, produces = "application/json")
    public ResponseEntity<?> replace(HttpServletRequest request) throws java.io.IOException {
        return signed(request, AccountMediaSessionsProtocol.REPLACE, AccountMediaSessionsProtocol.Replacement.class);
    }
    private <T> ResponseEntity<?> signed(HttpServletRequest request, String path, Class<T> type) throws java.io.IOException {
        try {
            var verified = DelegatedRequestBodies.read(request, mapper, verifier, AccountMediaSessionsProtocol.AUDIENCE,
                    path, type, AccountMediaSessionsProtocol.MAX_REQUEST_BYTES);
            return verified.call(() -> {
                String userId;
                if (verified.value() instanceof AccountMediaSessionsProtocol.Session command && AccountMediaSessionsProtocol.valid(command)) userId = command.userId();
                else if (verified.value() instanceof AccountMediaSessionsProtocol.Replacement command && AccountMediaSessionsProtocol.valid(command)) userId = command.userId();
                else throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
                var selected = profiles.resolveSelectedIdentity(userId);
                if (selected == null || !selected.equals(verified.principal().getProfileIdentity()) || !userId.equals(selected.userId()))
                    throw new AccessDeniedException("Actor mismatch");
                if (verified.value() instanceof AccountMediaSessionsProtocol.Session command) {
                    var token = owner.mediaToken(userId, command.sessionId());
                    return response(200, new AccountMediaSessionsProtocol.Token(token.isPresent(), token.map(AccountMediaSessions.MediaToken::hash).orElse(null)));
                }
                var command = (AccountMediaSessionsProtocol.Replacement) verified.value();
                return response(200, new AccountMediaSessionsProtocol.Decision(owner.replaceMediaToken(userId, command.sessionId(), command.tokenHash())));
            });
        } catch (AccessDeniedException denied) { return response(403, new AccountMediaSessionsProtocol.Error("INVALID_DELEGATION")); }
        catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new AccountMediaSessionsProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new AccountMediaSessionsProtocol.Error("MEDIA_SESSION_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object value) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(value); }
}
