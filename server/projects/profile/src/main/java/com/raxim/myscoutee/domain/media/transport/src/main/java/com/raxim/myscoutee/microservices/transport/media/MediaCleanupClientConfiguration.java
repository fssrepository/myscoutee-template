package com.raxim.myscoutee.microservices.transport.media;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns media to a different owner. */
@Configuration(proxyBeanMethods = false)
public class MediaCleanupClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteMediaCleanup remoteMediaCleanup(
            @Value("${myscoutee.remote.media-cleanup.url}") URI origin,
            @Value("${myscoutee.remote.media-cleanup.command-token}") String token,
            @Value("${myscoutee.remote.media-cleanup.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.media-cleanup.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.media-cleanup.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteMediaCleanup(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
