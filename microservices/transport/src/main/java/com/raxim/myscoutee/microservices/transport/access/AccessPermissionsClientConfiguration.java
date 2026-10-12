package com.raxim.myscoutee.microservices.transport.access;

import java.net.URI;
import java.time.*;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
@Import({com.raxim.myscoutee.microservices.transport.security.ActorDelegationSignerConfiguration.class,
        com.raxim.myscoutee.microservices.bootstrap.PolicyAuthorizationConfiguration.class})
public class AccessPermissionsClientConfiguration {
    @Bean(destroyMethod="close") RemoteAccessPermissions remoteAccessPermissions(
            @Value("${myscoutee.remote.access-permissions.url}") URI origin,
            @Value("${myscoutee.remote.access-permissions.read-token}") String token,
            @Value("${myscoutee.remote.access-permissions.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.access-permissions.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.access-permissions.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper,MeterRegistry metrics,ActorDelegation.Signer issuer) {
        return new RemoteAccessPermissions(origin,token,allowHttp,connectTimeout,requestTimeout,mapper,metrics,issuer);
    }
}
