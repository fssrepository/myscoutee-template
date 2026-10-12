package com.raxim.myscoutee.microservices.transport.platform;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns platform to a different owner. */
@Configuration(proxyBeanMethods = false)
public class AuthenticationModeClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteAuthenticationMode remoteAuthenticationMode(
            @Value("${myscoutee.remote.authentication-mode.url}") URI origin,
            @Value("${myscoutee.remote.authentication-mode.read-token}") String token,
            @Value("${myscoutee.remote.authentication-mode.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.authentication-mode.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.authentication-mode.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteAuthenticationMode(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
