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
public class EmailSendingClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteEmailSending remoteEmailSending(
            @Value("${myscoutee.remote.email-sending.url}") URI origin,
            @Value("${myscoutee.remote.email-sending.command-token}") String token,
            @Value("${myscoutee.remote.email-sending.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.email-sending.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.email-sending.request-timeout:PT30S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteEmailSending(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
