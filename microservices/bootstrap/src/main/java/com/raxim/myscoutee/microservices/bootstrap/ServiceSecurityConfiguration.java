package com.raxim.myscoutee.microservices.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** Internal metrics use a separate port. Domain routes need explicit transport authority. */
@Configuration(proxyBeanMethods = false)
public class ServiceSecurityConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain managementAccess(HttpSecurity http,
            @Value("${management.server.port:8081}") int port) throws Exception {
        return http.securityMatcher(request -> request.getLocalPort() == port)
                .authorizeHttpRequests(rules -> rules.requestMatchers("/health", "/metrics").permitAll()
                        .anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable).build();
    }

    @Bean
    @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "myscoutee.delegation.owner", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain unboundDomainAccess(HttpSecurity http) throws Exception {
        // Add authenticated service/public chains before this fallback with each transport adapter.
        return http.authorizeHttpRequests(rules -> rules.anyRequest().denyAll())
                .csrf(AbstractHttpConfigurer::disable).build();
    }
}
