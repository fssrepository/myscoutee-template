package com.raxim.myscoutee.microservices.transport.security;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Import({ActorDelegationSignerConfiguration.Owner.class, ActorDelegationSignerConfiguration.Consumer.class})
public class ActorDelegationSignerConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "myscoutee.delegation.owner", havingValue = "true")
    @Import(ActorDelegationIssuerConfiguration.class)
    static class Owner {}

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "myscoutee.delegation.owner", havingValue = "false")
    @Import(ActorDelegationVerifierConfiguration.class)
    static class Consumer {
        @Bean(destroyMethod = "close")
        RemoteActorDelegationSigner remoteActorDelegationSigner(
                @Value("${myscoutee.delegation.url}") URI origin,
                @Value("${myscoutee.delegation.client-token}") String token,
                @Value("${myscoutee.delegation.allow-insecure-http:false}") boolean allowHttp,
                @Value("${myscoutee.delegation.connect-timeout:PT1S}") Duration connectTimeout,
                @Value("${myscoutee.delegation.request-timeout:PT3S}") Duration requestTimeout,
                ObjectMapper mapper, MeterRegistry metrics, ActorDelegation.Verifier verifier) {
            return new RemoteActorDelegationSigner(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics, verifier);
        }
    }
}
