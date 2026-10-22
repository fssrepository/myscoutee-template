package com.raxim.myscoutee.microservices.transport.security;

import java.util.Map;
import com.raxim.myscoutee.microservices.transport.common.InternalReadCredentialFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Import({ActorDelegationIssuerConfiguration.class, ActorDelegationVerifierConfiguration.class, ActorDelegationBrokerEndpoint.class})
public class ActorDelegationBrokerServerConfiguration {
    @Bean ActorDelegationBrokerProtocol.Policies actorDelegationPolicies(
            @Value("${myscoutee.delegation.clients-json}") String json, ObjectMapper mapper) {
        if (json.length() > 1048576) throw new IllegalArgumentException("Delegation policy exceeds limit");
        try {
            var clients = mapper.readValue(json, new TypeReference<Map<String, ActorDelegationBrokerProtocol.ClientPolicy>>() {});
            return new ActorDelegationBrokerProtocol.Policies(clients);
        } catch (RuntimeException invalid) {
            // Configuration contains credentials: do not attach a parser exception with source text.
            throw new IllegalArgumentException("Invalid delegation policy configuration");
        }
    }
    // Reviewed service-only CSRF exemption; see ../myscoutee-sonarqube/REVIEWED_FINDINGS.md.
    @SuppressWarnings("java:S4502")
    @Bean @Order(90)
    SecurityFilterChain actorDelegationBrokerAccess(HttpSecurity http, ActorDelegationBrokerProtocol.Policies policies) throws Exception {
        var tokens = new java.util.LinkedHashMap<String, String>();
        policies.clients().forEach((name, client) -> tokens.put(name, client.token()));
        return http.securityMatcher(ActorDelegationBrokerProtocol.PATH)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        com.raxim.myscoutee.microservices.transport.common.InternalReadCredentialFilter::verifiedServiceRequest)).requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(rules -> rules.requestMatchers(HttpMethod.POST, ActorDelegationBrokerProtocol.PATH)
                        .hasAuthority(ActorDelegationBrokerProtocol.AUTHORITY).anyRequest().denyAll())
                .addFilterBefore(new InternalReadCredentialFilter(tokens, ActorDelegationBrokerProtocol.AUTHORITY),
                        CsrfFilter.class)
                .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, error) ->
                        InternalReadCredentialFilter.fail(response, 403, "FORBIDDEN")))
                .build();
    }
}
