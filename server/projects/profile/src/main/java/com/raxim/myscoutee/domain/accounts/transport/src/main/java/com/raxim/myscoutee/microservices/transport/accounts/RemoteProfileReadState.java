package com.raxim.myscoutee.microservices.transport.accounts;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import com.raxim.myscoutee.domain.accounts.api.ProfileReadState;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserMenuCountersDto;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class RemoteProfileReadState extends RemoteReadTransport implements ProfileReadState {
    private final ObjectMapper mapper;
    private final ActorDelegation.Signer signer;
    public RemoteProfileReadState(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics, ActorDelegation.Signer signer) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper; this.signer = signer;
    }
    @Override public UserMenuCountersDto queryMenuCounters(String userId) {
        var query = new ProfileReadStateProtocol.CounterQuery(userId);
        if (!ProfileReadStateProtocol.valid(query)) throw new IllegalArgumentException("Invalid counter query");
        byte[] bytes = mapper.writeValueAsBytes(query);
        String proof = signer.issueForCurrent(ProfileReadStateProtocol.AUDIENCE, "POST", ProfileReadStateProtocol.COUNTERS, bytes);
        return exchangeAuthorized(ProfileReadStateProtocol.COUNTERS, bytes, new TypeReference<UserMenuCountersDto>() {},
                "profile-read-state.counters", 1048576, value -> true, Map.of(ActorDelegation.HEADER, proof));
    }


}
