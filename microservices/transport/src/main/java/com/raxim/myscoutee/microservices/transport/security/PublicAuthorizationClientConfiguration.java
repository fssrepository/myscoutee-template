package com.raxim.myscoutee.microservices.transport.security;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.microservices.transport.common.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@Import(ActorDelegationVerifierConfiguration.class)
public class PublicAuthorizationClientConfiguration {
    @Bean(destroyMethod = "close") RemotePublicAuthorization remotePublicAuthorization(
            @Value("${myscoutee.delegation.url}") URI origin,
            @Value("${myscoutee.delegation.client-token}") String token,
            @Value("${myscoutee.delegation.allow-insecure-http:false}") boolean allowHttp,
            @Value("${spring.application.name}") String name,
            @Value("${myscoutee.delegation.connect-timeout:PT1S}") Duration connect,
            @Value("${myscoutee.delegation.request-timeout:PT3S}") Duration timeout,
            ObjectMapper mapper, MeterRegistry metrics) {
        return new RemotePublicAuthorization(origin, token, allowHttp, name, connect, timeout, mapper, metrics);
    }
    @Bean @Order(1000)
    SecurityFilterChain authorizedPublicRequests(HttpSecurity http, RemotePublicAuthorization owner,
            ActorDelegation.Verifier verifier, ObjectMapper mapper,
            @Value("${spring.application.name}") String name) throws Exception {
        return http.securityMatcher(request -> !request.getRequestURI().matches("/(?:_internal|metrics|health)(?:/.*)?"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                // This chain executes only after the owner has applied the canonical authorization rules.
                .authorizeHttpRequests(rules -> rules.anyRequest().permitAll())
                .addFilterBefore(new Authorize(owner, verifier, mapper, name), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    static final class Authorize extends OncePerRequestFilter {
        private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
        private final RemotePublicAuthorization owner;
        private final ActorDelegation.Verifier verifier;
        private final ObjectMapper mapper;
        private final String service;
        Authorize(RemotePublicAuthorization owner, ActorDelegation.Verifier verifier, ObjectMapper mapper, String service) {
            this.owner = owner; this.verifier = verifier; this.mapper = mapper; this.service = service;
        }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            byte[] random = new byte[32]; RANDOM.nextBytes(random);
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            PublicAuthorizationProtocol.Result result;
            try {
                var reply = owner.authorize(request, nonce);
                if (reply.statusCode() >= 400 && reply.statusCode() < 500) {
                    response.setStatus(reply.statusCode());
                    for (String header : java.util.List.of("Content-Type", "WWW-Authenticate", "Retry-After"))
                        reply.headers().allValues(header).forEach(value -> response.addHeader(header, value));
                    response.setHeader("Cache-Control", "no-store");
                    response.getOutputStream().write(reply.body()); return;
                }
                if (reply.statusCode() != 200 || !reply.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
                    throw new IllegalStateException("Authorization unavailable");
                result = mapper.readValue(reply.body(), PublicAuthorizationProtocol.Result.class);
                if (result == null || !("REAL".equals(result.mode()) || "DEMO".equals(result.mode()))
                        || result.actor() != null && result.mediaUserId() != null
                        || result.actor() != null && result.actor().length() > 8192
                        || result.mediaUserId() != null && (result.mediaUserId().isBlank() || result.mediaUserId().length() > 512))
                    throw new IllegalStateException("Invalid authorization result");
            } catch (RuntimeException unavailable) { InternalReadCredentialFilter.fail(response, 503, "AUTHORIZATION_UNAVAILABLE"); return; }
            var previousMode = MongoSessionModeContext.currentMode();
            try {
                MongoSessionModeContext.set(MongoSessionMode.valueOf(result.mode()));
                ActorDelegation.VerifiedPrincipal actor = null;
                try {
                    if (result.actor() != null) actor = verifier.verifyPrincipal(result.actor(), PublicAuthorizationProtocol.audience(service),
                            request.getMethod(), PublicAuthorizationProtocol.target(request), PublicAuthorizationProtocol.nonceBytes(nonce));
                } catch (RuntimeException invalid) { InternalReadCredentialFilter.fail(response, 503, "INVALID_AUTHORIZATION"); return; }
                try (var scope = actor == null ? DelegatedActorContext.withoutActor() : DelegatedActorContext.open(actor)) {
                    if (result.mediaUserId() != null) SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(result.mediaUserId(), null,
                                    java.util.List.of(new SimpleGrantedAuthority("MEDIA"))));
                    chain.doFilter(request, response);
                }
            } finally { MongoSessionModeContext.set(previousMode); }
        }
    }
}
