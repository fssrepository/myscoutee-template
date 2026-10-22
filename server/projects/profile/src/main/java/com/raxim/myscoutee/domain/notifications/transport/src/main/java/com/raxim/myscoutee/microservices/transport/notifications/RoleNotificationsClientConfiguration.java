package com.raxim.myscoutee.microservices.transport.notifications;

import java.net.URI;
import java.time.Duration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Imported only where the manifest assigns affinity to a different owner. */
@Configuration(proxyBeanMethods = false)
public class RoleNotificationsClientConfiguration {
    @Bean(destroyMethod = "close")
    RemoteRoleNotifications remoteRoleNotifications(
            @Value("${myscoutee.remote.role-notifications.url}") URI origin,
            @Value("${myscoutee.remote.role-notifications.command-token}") String token,
            @Value("${myscoutee.remote.role-notifications.allow-insecure-http:false}") boolean allowHttp,
            @Value("${myscoutee.remote.role-notifications.connect-timeout:PT1S}") Duration connectTimeout,
            @Value("${myscoutee.remote.role-notifications.request-timeout:PT3S}") Duration requestTimeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemoteRoleNotifications(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
}
