package com.raxim.myscoutee.microservices.transport.accounts;

import java.net.URI;
import java.time.*;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
@Import(com.raxim.myscoutee.microservices.transport.security.ActorDelegationSignerConfiguration.class)
public class ProfileReadStateClientConfiguration {
    @Bean(destroyMethod="close") RemoteProfileReadState remoteProfileReadState(
            @Value("${myscoutee.remote.profile-read-state.url}") URI origin,
            @Value("${myscoutee.remote.profile-read-state.read-token}") String token,
            @Value("${myscoutee.remote.profile-read-state.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.profile-read-state.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.profile-read-state.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper,MeterRegistry metrics,ActorDelegation.Signer issuer) {
        return new RemoteProfileReadState(origin,token,allowHttp,connectTimeout,requestTimeout,mapper,metrics,issuer);
    }
}
