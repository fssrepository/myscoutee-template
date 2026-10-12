package com.raxim.myscoutee.microservices.transport.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.common.security.http.HttpSecurityContribution;
import com.raxim.myscoutee.domain.accounts.api.AccountIdentity;
import com.raxim.myscoutee.microservices.transport.common.InternalReadCredentialFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** Uses the canonical filter chain; a verified private exchange stops before controller execution. */
@Configuration(proxyBeanMethods = false)
public class PublicAuthorizationOwnerConfiguration {
    @Bean HttpSecurityContribution publicAuthorizationContribution(ActorDelegationBrokerProtocol.Policies policies,
            ActorDelegation.Issuer issuer, ObjectMapper mapper) {
        return http -> http.addFilterBefore(new ValidateExchange(policies), SecurityContextHolderFilter.class)
                .addFilterAfter(new CompleteExchange(issuer, mapper), AuthorizationFilter.class);
    }

    static final class ValidateExchange extends OncePerRequestFilter {
        private final ActorDelegationBrokerProtocol.Policies policies;
        ValidateExchange(ActorDelegationBrokerProtocol.Policies policies) { this.policies = policies; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            String client = request.getHeader(PublicAuthorizationProtocol.CLIENT);
            String token = request.getHeader(PublicAuthorizationProtocol.TOKEN);
            String nonce = request.getHeader(PublicAuthorizationProtocol.NONCE);
            if (client == null && token == null && nonce == null) { chain.doFilter(request, response); return; }
            var policy = client == null ? null : policies.clients().get(client);
            String method = request.getHeader(PublicAuthorizationProtocol.METHOD);
            String peer = request.getHeader(PublicAuthorizationProtocol.PEER);
            if (!"POST".equals(request.getMethod()) || method == null || !method.matches("GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS")
                    || policy == null || token == null || token.length() > 128 || nonce == null
                    || !nonce.matches("[A-Za-z0-9_-]{43}") || peer == null || !peer.matches("[0-9a-fA-F:.]{1,64}")
                    || !policy.audiences().contains(PublicAuthorizationProtocol.audience(client))
                    || !MessageDigest.isEqual(policy.token().getBytes(StandardCharsets.US_ASCII), token.getBytes(StandardCharsets.US_ASCII))
                    || java.util.List.of(PublicAuthorizationProtocol.CLIENT, PublicAuthorizationProtocol.TOKEN,
                        PublicAuthorizationProtocol.NONCE, PublicAuthorizationProtocol.PEER, PublicAuthorizationProtocol.METHOD).stream()
                        .anyMatch(name -> Collections.list(request.getHeaders(name)).size() != 1)) {
                InternalReadCredentialFilter.fail(response, 401, "UNAUTHORIZED"); return;
            }
            request.setAttribute(PublicAuthorizationProtocol.ATTRIBUTE, new PublicAuthorizationProtocol.Exchange(client, nonce));
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override public String getMethod() { return method; }
                @Override public String getRemoteAddr() { return peer; }
                @Override public String getRemoteHost() { return peer; }
            }, response);
        }
    }

    static final class CompleteExchange extends OncePerRequestFilter {
        private final ActorDelegation.Issuer issuer;
        private final ObjectMapper mapper;
        CompleteExchange(ActorDelegation.Issuer issuer, ObjectMapper mapper) { this.issuer = issuer; this.mapper = mapper; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            Object attribute = request.getAttribute(PublicAuthorizationProtocol.ATTRIBUTE);
            if (!(attribute instanceof PublicAuthorizationProtocol.Exchange exchange)) { chain.doFilter(request, response); return; }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            String actor = null, media = null;
            if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
                if (authentication.getPrincipal() instanceof AccountIdentity) {
                    actor = issuer.issueForCurrent(PublicAuthorizationProtocol.audience(exchange.client()), request.getMethod(),
                            PublicAuthorizationProtocol.target(request), PublicAuthorizationProtocol.nonceBytes(exchange.nonce()));
                } else if (authentication.getPrincipal() instanceof String userId && !userId.isBlank() && userId.length() <= 512
                        && authentication.getAuthorities().size() == 1
                        && authentication.getAuthorities().iterator().next().getAuthority().equals("MEDIA")) {
                    media = userId;
                } else { InternalReadCredentialFilter.fail(response, 403, "UNSUPPORTED_PRINCIPAL"); return; }
            }
            response.setStatus(200);
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.getOutputStream().write(mapper.writeValueAsBytes(new PublicAuthorizationProtocol.Result(
                    MongoSessionModeContext.currentMode().name(), actor, media)));
        }
    }
}
