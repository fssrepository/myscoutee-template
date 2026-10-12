package com.raxim.myscoutee.microservices.transport.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** Restore the service authentication after a verified actor's owner operation, including errors. */
public final class DelegatedActorContext implements AutoCloseable {
    private final SecurityContext previous;

    private DelegatedActorContext(ActorDelegation.VerifiedPrincipal principal) {
        if (principal != null) principal.requireCurrentMode();
        previous = SecurityContextHolder.getContext();
        var current = SecurityContextHolder.createEmptyContext();
        if (principal != null) current.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(current);
    }

    public static DelegatedActorContext open(ActorDelegation.VerifiedPrincipal principal) {
        return new DelegatedActorContext(java.util.Objects.requireNonNull(principal));
    }

    public static DelegatedActorContext withoutActor() { return new DelegatedActorContext(null); }

    @Override public void close() { SecurityContextHolder.setContext(previous); }
}
