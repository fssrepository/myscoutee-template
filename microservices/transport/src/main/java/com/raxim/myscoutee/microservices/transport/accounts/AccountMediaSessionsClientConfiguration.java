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
public class AccountMediaSessionsClientConfiguration {
    @Bean(destroyMethod="close") RemoteAccountMediaSessions remoteAccountMediaSessions(
            @Value("${myscoutee.remote.account-media-sessions.url}") URI origin,
            @Value("${myscoutee.remote.account-media-sessions.command-token}") String token,
            @Value("${myscoutee.remote.account-media-sessions.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.account-media-sessions.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.account-media-sessions.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper,MeterRegistry metrics,ActorDelegation.Signer issuer) {
        return new RemoteAccountMediaSessions(origin,token,allowHttp,connectTimeout,requestTimeout,mapper,metrics,issuer);
    }
}
