package com.raxim.myscoutee.microservices.transport.help;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns help to a different owner. */
@Configuration(proxyBeanMethods = false)
public class HelpPrivacyClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteHelpPrivacy remoteHelpPrivacy(
            @Value("${myscoutee.remote.help-privacy.url}") URI origin,
            @Value("${myscoutee.remote.help-privacy.command-token}") String token,
            @Value("${myscoutee.remote.help-privacy.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.help-privacy.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.help-privacy.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteHelpPrivacy(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
