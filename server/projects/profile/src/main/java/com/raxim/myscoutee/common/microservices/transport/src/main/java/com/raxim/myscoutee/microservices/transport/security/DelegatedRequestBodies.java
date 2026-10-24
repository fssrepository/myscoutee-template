package com.raxim.myscoutee.microservices.transport.security;

import java.io.IOException;
import java.util.Collections;
import jakarta.servlet.http.HttpServletRequest;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.*;

/** Shared verification envelope; each endpoint supplies its explicit operation and DTO. */
public final class DelegatedRequestBodies {
    private DelegatedRequestBodies() {}
    public record Verified<T>(ActorDelegation.Actor actor, ActorDelegation.VerifiedPrincipal principal, T value) {
        public <R> R call(java.util.function.Supplier<R> owner) {
            try (var ignored = DelegatedActorContext.open(principal)) { return owner.get(); }
        }
    }
    public static <T> Verified<T> read(HttpServletRequest request,ObjectMapper mapper,ActorDelegation.Verifier verifier,
            String audience,String path,Class<T> type,int maxBytes) throws IOException {
        var tokens=Collections.list(request.getHeaders(ActorDelegation.HEADER));
        if(tokens.size()!=1)throw new AccessDeniedException("Actor delegation required");
        var bytes=ReadQueryBodies.readBytes(request,maxBytes);
        var principal=verifier.verifyPrincipal(tokens.getFirst(),audience,"POST",path,bytes);
        var identity=principal.getProfileIdentity();
        var actor=new ActorDelegation.Actor(identity.userId(), identity.accountUserId()==null ? "" : identity.accountUserId(),
                identity.workspaceGroupId()==null ? "" : identity.workspaceGroupId());
        return new Verified<>(actor, principal, decode(mapper, bytes, type));
    }
    private static <T> T decode(ObjectMapper mapper, byte[] bytes, Class<T> type) {
        T value;
        try {value=mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);}
        catch(RuntimeException invalid){throw new ReadQueryBodies.RejectedQuery(400,"INVALID_REQUEST");}
        if(value==null)throw new ReadQueryBodies.RejectedQuery(400,"INVALID_REQUEST");
        return value;
    }
}
