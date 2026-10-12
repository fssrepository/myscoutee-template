package com.raxim.myscoutee.microservices.transport.security;

import java.time.Clock;
import java.util.Base64;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

/** A live socket renews its short-lived delegation through the original session
 * authentication. It cannot extend a signed grant or change its selected actor. */
public final class SocketAuthorization {
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private final RemotePublicAuthorization owner;
    private final RemotePublicAuthorization.Request request;
    private final ActorDelegation.Verifier verifier;
    private final ObjectMapper mapper;
    private final String service;
    private final MongoSessionMode mode;
    private final Clock clock;
    private volatile Authentication authentication;

    public SocketAuthorization(RemotePublicAuthorization owner, RemotePublicAuthorization.Request request,
            ActorDelegation.Verifier verifier, ObjectMapper mapper, String service,
            MongoSessionMode mode, Authentication authentication, Clock clock) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ActorDelegation.VerifiedPrincipal))
            throw new AccessDeniedException("Authenticated socket required");
        this.owner = owner; this.request = request; this.verifier = verifier; this.mapper = mapper;
        this.service = service; this.mode = mode; this.authentication = authentication; this.clock = clock;
    }

    public Authentication authentication() { return authentication; }

    public synchronized void refreshIfNeeded() {
        var previous = (ActorDelegation.VerifiedPrincipal) authentication.getPrincipal();
        // Message bursts reuse the verified grant. No timer, per-message owner
        // authentication call or extension of the original 30-second grant.
        if (!previous.expiresWithin(clock.instant(), 5)) return;
        byte[] random = new byte[32]; RANDOM.nextBytes(random);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        var result = PublicAuthorizationProtocol.decode(owner.authorize(request, nonce), mapper);
        if (!mode.name().equals(result.mode()) || result.actor() == null || result.mediaUserId() != null)
            throw new AccessDeniedException("Socket authentication changed");
        var previousMode = MongoSessionModeContext.currentMode();
        try {
            MongoSessionModeContext.set(mode);
            var actor = verifier.verifyPrincipal(result.actor(), PublicAuthorizationProtocol.audience(service),
                    request.method(), request.target(), PublicAuthorizationProtocol.nonceBytes(nonce));
            if (!previous.getProfileIdentity().equals(actor.getProfileIdentity())
                    || !previous.getAuthorities().equals(actor.getAuthorities())
                    || !previous.getEmail().equals(actor.getEmail()))
                throw new AccessDeniedException("Socket authentication changed");
            authentication = UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities());
        } finally { MongoSessionModeContext.set(previousMode); }
    }
}
