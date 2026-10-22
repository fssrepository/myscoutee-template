package com.raxim.myscoutee.microservices.transport.administration;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns administration to a different owner. */
@Configuration(proxyBeanMethods = false)
public class OperationalTelemetryClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteOperationalTelemetry remoteOperationalTelemetry(
            @Value("${myscoutee.remote.operational-telemetry.url}") URI origin,
            @Value("${myscoutee.remote.operational-telemetry.command-token}") String token,
            @Value("${myscoutee.remote.operational-telemetry.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.operational-telemetry.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.operational-telemetry.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteOperationalTelemetry(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
