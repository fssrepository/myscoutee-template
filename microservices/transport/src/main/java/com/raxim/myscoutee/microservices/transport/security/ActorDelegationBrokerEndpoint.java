package com.raxim.myscoutee.microservices.transport.security;

import java.util.Map;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/** Derives only configured calls from an unexpired delegation addressed to the authenticated caller. */
@RestController
public final class ActorDelegationBrokerEndpoint {
    private final Map<String, ActorDelegationBrokerProtocol.ClientPolicy> clients;
    private final ActorDelegation.Issuer issuer;
    private final ActorDelegation.Verifier verifier;
    private final ObjectMapper mapper;
    public ActorDelegationBrokerEndpoint(ActorDelegationBrokerProtocol.Policies policies,
            ActorDelegation.Issuer issuer, ActorDelegation.Verifier verifier, ObjectMapper mapper) {
        this.clients = policies.clients(); this.issuer = issuer; this.verifier = verifier; this.mapper = mapper;
    }
    @PostMapping(value = ActorDelegationBrokerProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> derive(HttpServletRequest request) throws java.io.IOException {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            var policy = authentication == null || !authentication.isAuthenticated() ? null : clients.get(authentication.getName());
            if (policy == null) throw new AccessDeniedException("Unknown caller");
            byte[] bytes = ReadQueryBodies.readBytes(request, ActorDelegationBrokerProtocol.MAX_REQUEST_BYTES);
            ActorDelegationBrokerProtocol.Request query;
            try {
                query = mapper.readerFor(ActorDelegationBrokerProtocol.Request.class)
                        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!ActorDelegationBrokerProtocol.valid(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            if (!policy.operations().contains(query.operation())) throw new AccessDeniedException("Operation not delegated");
            var principal = verifier.verifyForRecipient(query.parent(), policy.audiences());
            var operation = query.operation();
            return response(200, new ActorDelegationBrokerProtocol.Result(issuer.issueDerived(
                    principal, operation.audience(), operation.method(), operation.path(), query.bodyDigest())));
        } catch (ReadQueryBodies.RejectedQuery invalid) {
            return response(invalid.status(), new ActorDelegationBrokerProtocol.Error(invalid.code()));
        } catch (AccessDeniedException denied) {
            return response(403, new ActorDelegationBrokerProtocol.Error("INVALID_DELEGATION"));
        }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
