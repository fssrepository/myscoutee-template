package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import com.raxim.myscoutee.microservices.transport.security.DelegatedRequestBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
public final class SelectedProfileEndpoint {
    private final SelectedProfile owner;
    private final ActorDelegation.Verifier verifier;
    private final ObjectMapper mapper;
    public SelectedProfileEndpoint(SelectedProfile owner, ActorDelegation.Verifier verifier, ObjectMapper mapper) {
        this.owner = owner; this.verifier = verifier; this.mapper = mapper;
    }
    @PostMapping(value = SelectedProfileProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> selected(HttpServletRequest request) throws java.io.IOException {
        try {
            var verified = DelegatedRequestBodies.read(request, mapper, verifier, SelectedProfileProtocol.AUDIENCE,
                    SelectedProfileProtocol.PATH, SelectedProfileProtocol.Query.class, SelectedProfileProtocol.MAX_REQUEST_BYTES);
            if (!SelectedProfileProtocol.valid(verified.value())) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            var selected = verified.call(() -> owner.resolveSelectedIdentity(verified.value().requestedUserId()));
            if (selected != null && !selected.equals(verified.principal().getProfileIdentity())) throw new AccessDeniedException("Actor mismatch");
            return response(200, new SelectedProfileProtocol.Result(true, selected));
        } catch (AccessDeniedException denied) { return response(403, new SelectedProfileProtocol.Error("INVALID_DELEGATION")); }
        catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new SelectedProfileProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new SelectedProfileProtocol.Error("PROFILE_SELECTION_UNAVAILABLE")); }
    }

    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
