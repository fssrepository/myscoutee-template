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
public class DeploymentBrandingClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteDeploymentBranding remoteDeploymentBranding(
            @Value("${myscoutee.remote.deployment-branding.url}") URI origin,
            @Value("${myscoutee.remote.deployment-branding.read-token}") String token,
            @Value("${myscoutee.remote.deployment-branding.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.deployment-branding.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.deployment-branding.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteDeploymentBranding(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
