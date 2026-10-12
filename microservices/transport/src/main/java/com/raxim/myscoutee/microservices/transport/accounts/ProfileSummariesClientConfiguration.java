package com.raxim.myscoutee.microservices.transport.accounts;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns accounts to a different owner. */
@Configuration(proxyBeanMethods = false)
public class ProfileSummariesClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteProfileSummaries remoteProfileSummaries(
            @Value("${myscoutee.remote.profile-summaries.url}") URI origin,
            @Value("${myscoutee.remote.profile-summaries.read-token}") String token,
            @Value("${myscoutee.remote.profile-summaries.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.profile-summaries.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.profile-summaries.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteProfileSummaries(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
