package com.raxim.myscoutee.common.config.mongo;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import com.raxim.myscoutee.domain.platform.api.OperatorBootstrapRequestSupport;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.common.security.media.MediaSessionAccessService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SessionMongoRoutingFilter extends OncePerRequestFilter {
    public static final String SESSION_KIND_HEADER = DemoSessionRequestSupport.SESSION_KIND_HEADER;
    private static final String DEMO_USERS_ROUTE = "/auth/demo-users";
    private final ConfigProperties configProperties;

    public SessionMongoRoutingFilter(ConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        MongoSessionModeContext.set(resolveMode(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MongoSessionModeContext.clear();
        }
    }

    private MongoSessionMode resolveMode(HttpServletRequest request) {
        String requestUri = request == null ? null : request.getRequestURI();
        String auth = request == null ? null : request.getHeader("Authorization");
        if ((requestUri != null && requestUri.matches("/(?:api/)?mcp/oauth/.*"))
                || (auth != null && auth.startsWith("Bearer mca_"))) {
            return configProperties != null && configProperties.isMcpDemoEnabled()
                    ? MongoSessionMode.DEMO : MongoSessionMode.REAL;
        }
        if (com.raxim.myscoutee.domain.media.api.MediaSessionAccess.isPrivateRead(request)) {
            return MediaSessionAccessService.sessionMode(request);
        }
        if (OperatorBootstrapRequestSupport.isLoginRequest(request)
                || OperatorBootstrapRequestSupport
                        .hasAuthorizationHeader(request)) {
            return MongoSessionMode.REAL;
        }
        if (requestUri != null && requestUri.endsWith(DEMO_USERS_ROUTE)) {
            return MongoSessionMode.DEMO;
        }
        if (DemoSessionRequestSupport.isOperatorRequest(request)) {
            if (DemoSessionRequestSupport.isDemoSessionRequest(request)
                    || configProperties == null
                    || !configProperties.isFirebaseEnabled()) {
                return MongoSessionMode.DEMO;
            }
            return MongoSessionMode.REAL;
        }
        if (DemoSessionRequestSupport.isDemoSessionRequest(request)
                && isAdminRequest(requestUri)) {
            return MongoSessionMode.DEMO;
        }
        if (DemoSessionRequestSupport.isAllowedDemoSessionRequest(request)) {
            return MongoSessionMode.DEMO;
        }
        if (configProperties != null && configProperties.isFirebaseEnabled()) {
            return MongoSessionMode.REAL;
        }
        return DemoSessionRequestSupport.isDemoSessionRequest(request)
                ? MongoSessionMode.DEMO
                : MongoSessionMode.REAL;
    }

    private boolean isAdminRequest(String requestUri) {
        return requestUri != null
                && (requestUri.startsWith("/admin")
                        || requestUri.startsWith("/api/admin"));
    }
}
