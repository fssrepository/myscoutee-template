package com.raxim.myscoutee.microservices.transport.localization;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns localization to a different owner. */
@Configuration(proxyBeanMethods = false)
public class LocalizationClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteTranslationBundles remoteTranslationBundles(
            @Value("${myscoutee.remote.localization.url}") URI origin,
            @Value("${myscoutee.remote.localization.read-token}") String token,
            @Value("${myscoutee.remote.localization.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.localization.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.localization.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteTranslationBundles(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
