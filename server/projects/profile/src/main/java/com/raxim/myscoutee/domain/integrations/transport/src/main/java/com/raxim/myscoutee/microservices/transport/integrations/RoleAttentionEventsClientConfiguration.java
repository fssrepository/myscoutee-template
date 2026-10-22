package com.raxim.myscoutee.microservices.transport.integrations;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns integrations to a different owner. */
@Configuration(proxyBeanMethods = false)
public class RoleAttentionEventsClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteRoleAttentionEvents remoteRoleAttentionEvents(
            @Value("${myscoutee.remote.role-attention-events.url}") URI origin,
            @Value("${myscoutee.remote.role-attention-events.command-token}") String token,
            @Value("${myscoutee.remote.role-attention-events.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.role-attention-events.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.role-attention-events.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteRoleAttentionEvents(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
