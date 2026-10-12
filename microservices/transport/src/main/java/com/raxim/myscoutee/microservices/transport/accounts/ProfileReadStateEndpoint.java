package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.ProfileReadState;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import com.raxim.myscoutee.microservices.transport.security.DelegatedRequestBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@RestController
public final class ProfileReadStateEndpoint {
    private final ProfileReadState owner;
    private final ActorDelegation.Verifier verifier;
    private final ObjectMapper mapper;
    public ProfileReadStateEndpoint(ProfileReadState owner, ActorDelegation.Verifier verifier, ObjectMapper mapper) {
        this.owner = owner; this.verifier = verifier; this.mapper = mapper;
    }
    @PostMapping(value = ProfileReadStateProtocol.COUNTERS, produces = "application/json")
    public ResponseEntity<?> counters(HttpServletRequest request) throws java.io.IOException {
        try {
            var verified = DelegatedRequestBodies.read(request, mapper, verifier, ProfileReadStateProtocol.AUDIENCE,
                    ProfileReadStateProtocol.COUNTERS, ProfileReadStateProtocol.CounterQuery.class, 16384);
            if (!ProfileReadStateProtocol.valid(verified.value())) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, java.util.Objects.requireNonNull(verified.call(() -> owner.queryMenuCounters(verified.value().userId()))));
        } catch (AccessDeniedException denied) { return response(403, new ProfileReadStateProtocol.Error("INVALID_DELEGATION")); }
        catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new ProfileReadStateProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new ProfileReadStateProtocol.Error("PROFILE_READ_STATE_UNAVAILABLE")); }
    }

    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
