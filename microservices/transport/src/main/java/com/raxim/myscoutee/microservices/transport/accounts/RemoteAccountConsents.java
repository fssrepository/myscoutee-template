package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.AccountConsents;
import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Bounded scoped call to the existing owner; no automatic retries. */
public final class RemoteAccountConsents extends RemoteReadTransport implements AccountConsents {
    private final ObjectMapper mapper;
    public RemoteAccountConsents(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public java.util.Optional<com.raxim.myscoutee.domain.accounts.api.AccountConsents.Consent> forRevisionOrLatest(String userId, String revisionId) {
        var command = new AccountConsentsProtocol.Query(AccountConsentsProtocol.Scope.current(), userId, revisionId);
        if (!AccountConsentsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-consents request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountConsentsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-consents request exceeds byte limit");
        var result = ownerExchange(AccountConsentsProtocol.REVISION, bytes, new TypeReference<AccountConsentsProtocol.Result>() {},
                "account-consents.revision", AccountConsentsProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.resolved()) && (v.value()==null || java.util.Objects.equals(userId,v.value().userId())));
        return java.util.Optional.ofNullable(result.value());
    }
    @Override public java.util.Optional<com.raxim.myscoutee.domain.accounts.api.AccountConsents.Consent> latest(String userId) {
        var command = new AccountConsentsProtocol.Query(AccountConsentsProtocol.Scope.current(), userId, null);
        if (!AccountConsentsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-consents request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountConsentsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-consents request exceeds byte limit");
        var result = ownerExchange(AccountConsentsProtocol.LATEST, bytes, new TypeReference<AccountConsentsProtocol.Result>() {},
                "account-consents.latest", AccountConsentsProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.resolved()) && (v.value()==null || java.util.Objects.equals(userId,v.value().userId())));
        return java.util.Optional.ofNullable(result.value());
    }
    @Override public com.raxim.myscoutee.domain.accounts.api.AccountConsents.Consent record(String userId, String revisionId, int version, java.util.List<String> optionalSections, String source) {
        var command = new AccountConsentsProtocol.Save(AccountConsentsProtocol.Scope.current(), userId, revisionId, version, optionalSections, source);
        if (!AccountConsentsProtocol.valid(command)) throw new IllegalArgumentException("Invalid account-consents request");
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountConsentsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("account-consents request exceeds byte limit");
        var result = ownerExchange(AccountConsentsProtocol.RECORD, bytes, new TypeReference<AccountConsentsProtocol.Result>() {},
                "account-consents.record", AccountConsentsProtocol.MAX_RESPONSE_BYTES, v -> Boolean.TRUE.equals(v.resolved()));
        return result.value();
    }
    private <T> T ownerExchange(String path, byte[] bytes, TypeReference<T> type, String operation, int limit,
            java.util.function.Predicate<T> valid) {
        try { return exchange(path, bytes, type, operation, limit, valid); }
        catch (RemoteReadException failure) {
            if (failure.status() >= 400 && failure.status() < 500 && "OWNER_REJECTED".equals(failure.remoteCode()))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(failure.status()));
            throw failure;
        }
    }

}
