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
public class EmailSecretsClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteEmailSecrets remoteEmailSecrets(
            @Value("${myscoutee.remote.email-secrets.url}") URI origin,
            @Value("${myscoutee.remote.email-secrets.command-token}") String token,
            @Value("${myscoutee.remote.email-secrets.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.email-secrets.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.email-secrets.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteEmailSecrets(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
