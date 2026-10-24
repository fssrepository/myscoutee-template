package com.raxim.myscoutee.microservices.transport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class ActorDelegationIssuerConfiguration {
    @Bean ActorDelegation.Issuer actorDelegationIssuer(
            @Value("${myscoutee.delegation.private-key}") String privateKey,
            @Value("${myscoutee.delegation.public-key}") String publicKey) {
        return new ActorDelegation.Issuer(privateKey, publicKey,java.time.Clock.systemUTC());
    }
}
