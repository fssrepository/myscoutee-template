package com.raxim.myscoutee.microservices.transport.accounts;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import com.raxim.myscoutee.domain.accounts.api.AccountDevices;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Single-attempt commands; a transport failure is an unknown outcome, never an automatic retry. */
public final class RemoteAccountDevices extends RemoteReadTransport implements AccountDevices {
    private final ObjectMapper mapper;
    private final ActorDelegation.Signer signer;
    public RemoteAccountDevices(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics, ActorDelegation.Signer signer) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
        this.mapper = mapper; this.signer = signer;
    }
    @Override public void register(String userId, String deviceId, String firebaseToken, String platform, Boolean enabled) {
        if (!AccountDevicesProtocol.valid(userId, deviceId, firebaseToken) || !AccountDevicesProtocol.bounded(platform, 512))
            throw new IllegalArgumentException("Invalid device registration");
        send(AccountDevicesProtocol.REGISTER, new AccountDevicesProtocol.Register(userId, deviceId, firebaseToken, platform, enabled));
    }
    @Override public void remove(String userId, String deviceId, String firebaseToken) {
        if (!AccountDevicesProtocol.valid(userId, deviceId, firebaseToken)) throw new IllegalArgumentException("Invalid device removal");
        send(AccountDevicesProtocol.REMOVE, new AccountDevicesProtocol.Remove(userId, deviceId, firebaseToken));
    }
    private void send(String path, Object command) {
        byte[] bytes = mapper.writeValueAsBytes(command);
        if (bytes.length > AccountDevicesProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Device command exceeds byte limit");
        String proof = signer.issueForCurrent(AccountDevicesProtocol.AUDIENCE, "POST", path, bytes);
        exchangeAuthorized(path, bytes, new TypeReference<AccountDevicesProtocol.Receipt>() {},
                "account-devices.command", AccountDevicesProtocol.MAX_RESPONSE_BYTES,
                value -> Boolean.TRUE.equals(value.completed()), Map.of(ActorDelegation.HEADER, proof));
    }
}
