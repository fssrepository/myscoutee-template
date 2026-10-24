package com.raxim.myscoutee.domain.integrations.config;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService;
import com.raxim.myscoutee.domain.integrations.service.IntegrationRoleAccessService;
import com.raxim.myscoutee.domain.integrations.service.McpWorkspaceAccess;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.AuthenticatedIntegrationClient;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class IntegrationTokenAuthenticationFilter extends OncePerRequestFilter {
    private final com.raxim.myscoutee.domain.accounts.api.ProfileAuthentication profileAuthentication;
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String CLIENT_ID_HEADER = "X-MyScoutee-Client-Id";

    private final IntegrationTokenService tokenService;
    private final org.springframework.beans.factory.ObjectProvider<com.raxim.myscoutee.domain.integrations.service.McpAuthorizationService> mcp;

    private final ProfileDirectory users;
    private final IntegrationRoleAccessService roles;
    private final java.util.Optional<McpWorkspaceAccess> workspaces;

    public IntegrationTokenAuthenticationFilter(IntegrationTokenService tokenService,
            ProfileDirectory users, java.util.Optional<McpWorkspaceAccess> workspaces, IntegrationRoleAccessService roles,
            com.raxim.myscoutee.domain.accounts.api.ProfileAuthentication profileAuthentication,
            org.springframework.beans.factory.ObjectProvider<com.raxim.myscoutee.domain.integrations.service.McpAuthorizationService> mcp) {
        this.profileAuthentication = profileAuthentication;
        this.mcp = mcp;

        this.tokenService = tokenService;
        this.users = users;
        this.workspaces = workspaces;
        this.roles = roles;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        return !(path.startsWith("/integrations/v1/") || path.startsWith("/api/integrations/v1/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        String authorization = request.getHeader("Authorization");
        String rawToken = StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length()).trim()
                : null;
        AuthenticatedIntegrationClient client = rawToken != null && rawToken.startsWith("mca_")
                ? mcp.getObject().authenticate(rawToken) : tokenService.authenticateClient(
                rawToken,
                request.getHeader(CLIENT_ID_HEADER),
                resolveClientAddress(request));
        if (client == null || !StringUtils.hasText(client.ownerUserId())) {
            SecurityContextHolder.clearContext();
            response.setHeader("Cache-Control", "no-store");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A valid integration token is required.");
            return;
        }

        // A key belongs to the profile that issued it; changing the interactive
        // session's active group must never retarget a running integration.
        SecurityContextHolder.clearContext();
        var owner = users.findByUserId(client.ownerUserId()).orElse(null);
        if (owner == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try { roles.requireActive(owner); }
        catch (ResponseStatusException denied) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        var permissions = owner.getIntegrationAccess() == null ? IntegrationAccess.initial() : owner.getIntegrationAccess();
        var mode = permissions.effective(client.tokenId(), IntegrationTokenService.MCP_SCOPE.equals(client.scope()));
        if (mode == IntegrationAccess.Mode.BLOCKED) {
            response.setHeader("Cache-Control", "no-store");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Integration access is blocked.");
            return;
        }
        client = client.withAccess(mode);
        try {
            if (StringUtils.hasText(owner.getWorkspaceGroupId())) {
                workspaces.orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
                        .requireProfile(owner.getUserId(), owner);
            }
        } catch (ResponseStatusException error) {
            response.sendError(error.getStatusCode().value());
            return;
        }
        var principal = profileAuthentication.integrationPrincipal(owner);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("INTEGRATION")));
        authentication.setDetails(client);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private String resolveClientAddress(HttpServletRequest request) {
        String directAddress = request == null ? null : request.getRemoteAddr();
        if (request == null || !isTrustedProxyAddress(directAddress)) {
            return directAddress;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String firstAddress = forwardedFor.split(",", 2)[0].trim();
            if (isNumericAddress(firstAddress)) {
                return firstAddress;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        return isNumericAddress(realIp) ? realIp.trim() : directAddress;
    }

    private boolean isTrustedProxyAddress(String value) {
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress();
        } catch (RuntimeException | IOException ignored) {
            return false;
        }
    }

    private boolean isNumericAddress(String value) {
        if (!StringUtils.hasText(value) || !value.trim().matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            InetAddress.getByName(value.trim());
            return true;
        } catch (RuntimeException | IOException ignored) {
            return false;
        }
    }
}
