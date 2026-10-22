package com.raxim.myscoutee.microservices.transport.notifications;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns notifications to a different owner. */
@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(com.raxim.myscoutee.microservices.transport.security.ActorDelegationSignerConfiguration.class)
public class NotificationReadsClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteNotificationReads remoteNotificationReads(
            @Value("${myscoutee.remote.notification-reads.url}") URI origin,
            @Value("${myscoutee.remote.notification-reads.read-token}") String token,
            @Value("${myscoutee.remote.notification-reads.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.notification-reads.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.notification-reads.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics, com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Signer signer) {
        return new RemoteNotificationReads(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics, signer);
    }
}
