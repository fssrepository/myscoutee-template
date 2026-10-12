package com.raxim.myscoutee.microservices.transport.email;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns email to a different owner. */
@Configuration(proxyBeanMethods = false)
public class EmailLinksClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteEmailLinks remoteEmailLinks(
            @Value("${myscoutee.remote.email-links.url}") URI origin,
            @Value("${myscoutee.remote.email-links.read-token}") String token,
            @Value("${myscoutee.remote.email-links.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.email-links.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.email-links.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteEmailLinks(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
