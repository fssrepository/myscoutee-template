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
public class NotificationJobsClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteNotificationJobs remoteNotificationJobs(
            @Value("${myscoutee.remote.notification-jobs.url}") URI origin,
            @Value("${myscoutee.remote.notification-jobs.command-token}") String token,
            @Value("${myscoutee.remote.notification-jobs.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.notification-jobs.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.notification-jobs.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteNotificationJobs(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
