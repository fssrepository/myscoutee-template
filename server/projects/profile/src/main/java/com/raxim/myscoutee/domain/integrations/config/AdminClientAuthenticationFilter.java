package com.raxim.myscoutee.domain.integrations.config;

import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService;
import com.raxim.myscoutee.domain.integrations.service.AdminClientAccessService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

@Component
public class AdminClientAuthenticationFilter extends OncePerRequestFilter {
    private final IntegrationTokenService tokens;
    private final AdminClientAccessService access;
    public AdminClientAuthenticationFilter(IntegrationTokenService tokens, AdminClientAccessService access) { this.tokens = tokens; this.access = access; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/admin-client/v1/") || path.startsWith("/api/admin-client/v1/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        String header = request.getHeader("Authorization");
        var client = tokens.authenticateClient(header != null && header.startsWith("Bearer ") ? header.substring(7) : null,
                request.getHeader(IntegrationTokenAuthenticationFilter.CLIENT_ID_HEADER), request.getRemoteAddr(), IntegrationTokenService.ADMIN_SCOPE);
        SecurityContextHolder.clearContext();
        if (client == null) { response.sendError(401); return; }
        try {
            var owner = access.requireAdmin(client.ownerUserId());
            var permissions = owner.getIntegrationAccess();
            if (permissions != null && permissions.clientMode(client.tokenId()) == com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess.Mode.BLOCKED) {
                response.sendError(403); return;
            }
        }
        catch (ResponseStatusException denied) { response.sendError(403); return; }
        var authentication = new UsernamePasswordAuthenticationToken(client.ownerUserId(), null, List.of(new SimpleGrantedAuthority("ADMIN_CLIENT")));
        authentication.setDetails(client); SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
