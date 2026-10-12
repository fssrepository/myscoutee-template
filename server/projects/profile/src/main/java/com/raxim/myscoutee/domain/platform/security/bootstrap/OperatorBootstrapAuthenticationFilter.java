package com.raxim.myscoutee.domain.platform.security.bootstrap;
import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapTransportSecurity;
import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapAuthenticationService;
import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapTokenInvalidException;
import com.raxim.myscoutee.domain.platform.api.OperatorBootstrapRequestSupport;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import com.raxim.myscoutee.domain.accounts.api.ProfileAuthentication;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OperatorBootstrapAuthenticationFilter
        extends OncePerRequestFilter {
    private final OperatorBootstrapAuthenticationService service;
    private final RuntimeConfiguration config;
    private final ProfileAuthentication profileAuthentication;

    public OperatorBootstrapAuthenticationFilter(
            OperatorBootstrapAuthenticationService service,
            RuntimeConfiguration config, ProfileAuthentication profileAuthentication) {
        this.service = service;
        this.config = config;
        this.profileAuthentication = profileAuthentication;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        if (OperatorBootstrapRequestSupport.isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = OperatorBootstrapRequestSupport.extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!OperatorBootstrapTransportSecurity.isAllowed(
                request,
                config)) {
            SecurityContextHolder.clearContext();
            response.sendError(
                    426,
                    "HTTPS is required for operator bootstrap authorization.");
            return;
        }
        if (token.isBlank()
                || DemoSessionRequestSupport.isDemoSessionRequest(request)) {
            SecurityContextHolder.clearContext();
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid operator bootstrap authorization.");
            return;
        }
        try {
            UserDetails principal = service.authenticateToken(token);
            SecurityContextHolder.getContext().setAuthentication(
                    profileAuthentication.trustedPrincipal(principal));
            filterChain.doFilter(request, response);
        } catch (OperatorBootstrapTokenInvalidException exception) {
            SecurityContextHolder.clearContext();
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid operator bootstrap authorization.");
        }
    }
}
