package com.raxim.myscoutee.microservices.transport.accounts;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import com.raxim.myscoutee.domain.accounts.api.AccountMediaSessions;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Hash checks prove the cookie; token reads/rotation additionally require signed profile authority. */
public final class RemoteAccountMediaSessions extends RemoteReadTransport implements AccountMediaSessions {
    private final ObjectMapper mapper;
    private final ActorDelegation.Signer signer;
    public RemoteAccountMediaSessions(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics, ActorDelegation.Signer signer) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper; this.signer = signer;
    }
    @Override public boolean acceptsMediaToken(String userId, String tokenHash) {
        var command = new AccountMediaSessionsProtocol.Check(userId, tokenHash);
        return exchange(AccountMediaSessionsProtocol.ACCEPTS, encode(command, AccountMediaSessionsProtocol.valid(command)),
                new TypeReference<AccountMediaSessionsProtocol.Decision>() {}, "media-sessions.accepts",
                AccountMediaSessionsProtocol.MAX_RESPONSE_BYTES, value -> value.value() != null).value();
    }
    @Override public Optional<MediaToken> mediaToken(String userId, String sessionId) {
        var command = new AccountMediaSessionsProtocol.Session(userId, sessionId);
        var bytes = encode(command, AccountMediaSessionsProtocol.valid(command));
        var result = exchangeAuthorized(AccountMediaSessionsProtocol.CURRENT, bytes, new TypeReference<AccountMediaSessionsProtocol.Token>() {},
                "media-sessions.current", AccountMediaSessionsProtocol.MAX_RESPONSE_BYTES, AccountMediaSessionsProtocol::valid,
                proof(AccountMediaSessionsProtocol.CURRENT, bytes));
        return result.found() ? Optional.of(new MediaToken(result.hash())) : Optional.empty();
    }
    @Override public boolean replaceMediaToken(String userId, String sessionId, String tokenHash) {
        var command = new AccountMediaSessionsProtocol.Replacement(userId, sessionId, tokenHash);
        var bytes = encode(command, AccountMediaSessionsProtocol.valid(command));
        return exchangeAuthorized(AccountMediaSessionsProtocol.REPLACE, bytes, new TypeReference<AccountMediaSessionsProtocol.Decision>() {},
                "media-sessions.replace", AccountMediaSessionsProtocol.MAX_RESPONSE_BYTES, value -> value.value() != null,
                proof(AccountMediaSessionsProtocol.REPLACE, bytes)).value();
    }
    private Map<String,String> proof(String path, byte[] bytes) {
        return Map.of(ActorDelegation.HEADER, signer.issueForCurrent(AccountMediaSessionsProtocol.AUDIENCE, "POST", path, bytes));
    }
    private byte[] encode(Object command, boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid media session request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountMediaSessionsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Media session request exceeds limit");
        return bytes;
    }
}
