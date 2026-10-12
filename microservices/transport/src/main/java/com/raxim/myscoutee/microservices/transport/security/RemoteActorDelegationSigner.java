package com.raxim.myscoutee.microservices.transport.security;

import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Requests a child operation from the account owner; this service has no signing key. */
public final class RemoteActorDelegationSigner extends RemoteReadTransport implements ActorDelegation.Signer {
    private final ObjectMapper mapper;
    private final ActorDelegation.Verifier verifier;
    public RemoteActorDelegationSigner(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics, ActorDelegation.Verifier verifier) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
        this.mapper = mapper; this.verifier = verifier;
    }
    @Override public String issueForCurrent(String audience, String method, String path, byte[] body) {
        return issue(current().getUserId(), audience, method, path, body);
    }
    @Override public String issue(String userId, String audience, String method, String path, byte[] body) {
        var principal = current();
        if (!principal.getUserId().equals(userId)) throw new AccessDeniedException("Actor mismatch");
        var query = new ActorDelegationBrokerProtocol.Request(principal.delegation(),
                new ActorDelegationBrokerProtocol.Operation(audience, method, path), ActorDelegation.digest(body));
        if (!ActorDelegationBrokerProtocol.valid(query)) throw new IllegalArgumentException("Invalid delegation operation");
        byte[] bytes = mapper.writeValueAsBytes(query);
        if (bytes.length > ActorDelegationBrokerProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Delegation request exceeds limit");
        var result = exchange(ActorDelegationBrokerProtocol.PATH, bytes,
                new TypeReference<ActorDelegationBrokerProtocol.Result>() {}, "actor-delegation.derive", 16384,
                value -> value.token() != null && value.token().length() <= 8192);
        var verified = verifier.verifyPrincipal(result.token(), audience, method, path, body);
        if (!principal.getProfileIdentity().equals(verified.getProfileIdentity())
                || !principal.getEmail().equals(verified.getEmail())
                || !principal.getAuthorities().equals(verified.getAuthorities()))
            throw new AccessDeniedException("Derived actor mismatch");
        return result.token();
    }
    private ActorDelegation.VerifiedPrincipal current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ActorDelegation.VerifiedPrincipal principal))
            throw new AccessDeniedException("Verified incoming actor delegation required");
        principal.requireCurrentMode();
        return principal;
    }
}
