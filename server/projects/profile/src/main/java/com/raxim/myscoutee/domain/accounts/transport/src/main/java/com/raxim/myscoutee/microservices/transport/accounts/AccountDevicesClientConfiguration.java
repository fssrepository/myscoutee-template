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
public class AccountDevicesClientConfiguration {
    @Bean(destroyMethod="close") RemoteAccountDevices remoteAccountDevices(
            @Value("${myscoutee.remote.account-devices.url}") URI origin,
            @Value("${myscoutee.remote.account-devices.command-token}") String token,
            @Value("${myscoutee.remote.account-devices.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.account-devices.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.account-devices.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper,MeterRegistry metrics,ActorDelegation.Signer issuer) {
        return new RemoteAccountDevices(origin,token,allowHttp,connectTimeout,requestTimeout,mapper,metrics,issuer);
    }
}
