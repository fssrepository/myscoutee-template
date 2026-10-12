package com.raxim.myscoutee.microservices.transport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class ActorDelegationVerifierConfiguration {
    @Bean ActorDelegation.Verifier actorDelegationVerifier(
            @Value("${myscoutee.delegation.public-key}") String publicKey) {
        return new ActorDelegation.Verifier(publicKey,java.time.Clock.systemUTC());
    }
}
