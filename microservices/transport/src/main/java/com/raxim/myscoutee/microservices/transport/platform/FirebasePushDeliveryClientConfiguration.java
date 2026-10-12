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
public class FirebasePushDeliveryClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteFirebasePushDelivery remoteFirebasePushDelivery(
            @Value("${myscoutee.remote.firebase-push-delivery.url}") URI origin,
            @Value("${myscoutee.remote.firebase-push-delivery.command-token}") String token,
            @Value("${myscoutee.remote.firebase-push-delivery.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.firebase-push-delivery.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.firebase-push-delivery.request-timeout:PT30S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteFirebasePushDelivery(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
