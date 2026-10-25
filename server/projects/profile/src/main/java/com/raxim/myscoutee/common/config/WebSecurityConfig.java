package com.raxim.myscoutee.common.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.data.repository.query.SecurityEvaluationContextExtension;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import com.raxim.myscoutee.domain.accounts.api.SessionRequestSupport;
import com.raxim.myscoutee.domain.accounts.security.session.AccountSessionFilter;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.common.config.mongo.SessionMongoRoutingFilter;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseService;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService;
import com.raxim.myscoutee.common.security.media.MediaSessionAccessService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    private final FirebaseService firebaseService;
    private final ConfigProperties configProperties;
    private final UserLoginSessionService userLoginSessionService;
    private final MediaSessionAccessService mediaSessionAccess;
    private final com.raxim.myscoutee.domain.integrations.config.IntegrationTokenAuthenticationFilter integrationTokenAuthenticationFilter;
    private final com.raxim.myscoutee.domain.integrations.config.AdminClientAuthenticationFilter adminClientAuthenticationFilter;

    public WebSecurityConfig(FirebaseService firebaseService,
            ConfigProperties configProperties,
            UserLoginSessionService userLoginSessionService,
            MediaSessionAccessService mediaSessionAccess,
            com.raxim.myscoutee.domain.integrations.config.IntegrationTokenAuthenticationFilter integrationTokenAuthenticationFilter,
            com.raxim.myscoutee.domain.integrations.config.AdminClientAuthenticationFilter adminClientAuthenticationFilter,
            org.springframework.beans.factory.ObjectProvider<com.raxim.myscoutee.common.security.http.HttpSecurityContribution> securityContributions) {
        this.integrationTokenAuthenticationFilter = integrationTokenAuthenticationFilter;
        this.adminClientAuthenticationFilter = adminClientAuthenticationFilter;
        this.securityContributions = securityContributions;

        this.firebaseService = firebaseService;
        this.configProperties = configProperties;
        this.userLoginSessionService = userLoginSessionService;
        this.mediaSessionAccess = mediaSessionAccess;
    }

    private final org.springframework.beans.factory.ObjectProvider<com.raxim.myscoutee.common.security.http.HttpSecurityContribution> securityContributions;

    @Bean
    // Authentication for mutations is header-based; media cookies are GET/HEAD-only. See WebSecurityConfigSessionTest and AccountSessionFilterPublicDocumentsTest.
    @SuppressWarnings("java:S4502")
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.addFilterBefore(sessionMongoRoutingFilter(), BasicAuthenticationFilter.class)
                .addFilterAfter(integrationTokenAuthenticationFilter, SessionMongoRoutingFilter.class)
                .addFilterAfter(adminClientAuthenticationFilter, com.raxim.myscoutee.domain.integrations.config.IntegrationTokenAuthenticationFilter.class)
                .addFilterAfter(tokenAuthorizationFilter(), com.raxim.myscoutee.domain.integrations.config.AdminClientAuthenticationFilter.class)
                .authorizeHttpRequests(authorizeRequests -> {
                    authorizeRequests.requestMatchers("/admin-client/v1/**", "/api/admin-client/v1/**").hasAuthority("ADMIN_CLIENT");
                    authorizeRequests.requestMatchers("/shutdown").denyAll();
                    authorizeRequests.requestMatchers("/integrations/v1/**", "/api/integrations/v1/**")
                            .hasAuthority("INTEGRATION");
                    authorizeRequests.requestMatchers("/media/private", "/api/media/private").hasAuthority("MEDIA");
                    authorizeRequests.requestMatchers("/metrics").hasAuthority("A");
                    authorizeRequests.requestMatchers("/operator/**", "/api/operator/**").hasAuthority("O");
                    authorizeRequests.requestMatchers(
                            "/mcp/oauth/metadata", "/mcp/oauth/resource", "/mcp/oauth/token", "/mcp/oauth/register",
                            "/api/mcp/oauth/metadata", "/api/mcp/oauth/resource", "/api/mcp/oauth/token", "/api/mcp/oauth/register",
                            "/public/email/unsubscribe",
                            "/public/email/unsubscribe/status",
                            "/api/public/email/unsubscribe",
                            "/api/public/email/unsubscribe/status",
                            "/health",
                            "/error",
                            "/auth/eligibility",
                            "/auth/demo-users",
                            "/api/auth/demo-users",
                            SessionRequestSupport.LOGIN_PATH,
                            SessionRequestSupport.API_LOGIN_PATH,
                            "/auth/facebook/data-deletion",
                            "/auth/facebook/data-deletion/status",
                            "/i18n/bundle",
                            "/api/i18n/bundle",
                            "/deployment/configuration",
                            "/api/deployment/configuration",
                            "/deployment/configuration/firebase-config",
                            "/api/deployment/configuration/firebase-config",
                            "/landing/**",
                            "/help/active",
                            "/privacy/active",
                            "/terms/active",
                            "/explanation/active",
                            "/css/**",
                            "/js/**",
                            "/img/**",
                            "/lib/**",
                            "/favicon.ico",
                            "/user/profile/images/**",
                            "/media/public",
                            "/media/public/**")
                            .permitAll();
                    if (configProperties.isChatOnlyMode()) {
                        authorizeRequests.requestMatchers("/activities/chats/**").authenticated()
                                .anyRequest().denyAll();
                    } else {
                        authorizeRequests.requestMatchers(DemoSessionRequestSupport::isAllowedDemoSessionRequest).permitAll();
                        authorizeRequests.requestMatchers("/admin/**", "/api/admin/**").hasAuthority("A")
                                .anyRequest().authenticated();
                    }
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .referrerPolicy(referrerPolicy -> referrerPolicy
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissionsPolicy -> permissionsPolicy
                                .policy("geolocation=(), microphone=(), camera=()")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> response.sendError(401))
                        .accessDeniedHandler((request, response, error) -> response.sendError(403)))
                .httpBasic(httpBasic -> httpBasic.disable())
                .csrf(csrf -> csrf.disable());
        if (securityContributions != null) {
            for (var contribution : securityContributions.orderedStream().toList()) contribution.configure(http);
        }
        return http.build();
    }

    private AccountSessionFilter tokenAuthorizationFilter() {
        return new AccountSessionFilter(firebaseService, configProperties, userLoginSessionService, mediaSessionAccess);
    }

    private SessionMongoRoutingFilter sessionMongoRoutingFilter() {
        return new SessionMongoRoutingFilter(configProperties);
    }

    @Bean
    SecurityEvaluationContextExtension securityEvaluationContextExtension() {
        return new SecurityEvaluationContextExtension();
    }
}
