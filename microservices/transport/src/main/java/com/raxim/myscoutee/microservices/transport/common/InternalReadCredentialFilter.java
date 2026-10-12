package com.raxim.myscoutee.microservices.transport.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Dedicated internal read capability. Never treats caller headers as a user principal. */
public final class InternalReadCredentialFilter extends OncePerRequestFilter {
    public static final String MODE_HEADER = "X-MyScoutee-Session-Mode";
    /** Distinguishes a verified transport capability from an authenticated application user. */
    public static final class ServiceAuthentication extends UsernamePasswordAuthenticationToken {
        private ServiceAuthentication(String caller, String authority) {
            super(caller, null, List.of(new SimpleGrantedAuthority(authority)));
        }
    }
    private final java.util.Map<String, byte[]> expected;
    private final String authority;
    public InternalReadCredentialFilter(String token, String authority) {
        this(java.util.Map.of("internal-reader", token), authority);
    }
    /** Named caller capabilities for the identity-owned delegation broker. */
    public InternalReadCredentialFilter(java.util.Map<String, String> callers, String authority) {
        if (callers == null || callers.size() > 64
                || new java.util.HashSet<>(callers.values()).size() != callers.size())
            throw new IllegalArgumentException("Distinct bounded caller credentials required");
        var credentials = new java.util.LinkedHashMap<String, byte[]>();
        callers.forEach((caller, token) -> {
            if (caller == null || !caller.matches("[a-z][a-z0-9-]{0,63}"))
                throw new IllegalArgumentException("Invalid service identity");
            credentials.put(caller, ("Bearer " + requireToken(token)).getBytes(StandardCharsets.US_ASCII));
        });
        expected = java.util.Collections.unmodifiableMap(credentials);
        this.authority = authority;
    }
    public static String requireToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43,128}"))
            throw new IllegalArgumentException("Internal read credential must be 43–128 URL-safe characters.");
        return token;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authorization = Collections.list(request.getHeaders("Authorization"));
        String caller = null;
        if (authorization.size() == 1 && authorization.getFirst().length() <= 135) {
            byte[] provided = authorization.getFirst().getBytes(StandardCharsets.US_ASCII);
            for (var candidate : expected.entrySet()) {
                if (MessageDigest.isEqual(candidate.getValue(), provided)) caller = candidate.getKey();
            }
        }
        if (caller == null) {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"internal-read\"");
            fail(response, 401, "UNAUTHORIZED"); return;
        }
        var modes = Collections.list(request.getHeaders(MODE_HEADER));
        if (modes.size() != 1 || !("REAL".equals(modes.getFirst()) || "DEMO".equals(modes.getFirst()))) {
            fail(response, 400, "INVALID_SESSION_MODE"); return;
        }
        var previousMode = MongoSessionModeContext.currentMode();
        var previousSecurity = SecurityContextHolder.getContext();
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new ServiceAuthentication(caller, authority));
        try {
            SecurityContextHolder.setContext(security);
            MongoSessionModeContext.set(MongoSessionMode.valueOf(modes.getFirst()));
            chain.doFilter(request, response);
        } finally {
            MongoSessionModeContext.set(previousMode);
            SecurityContextHolder.setContext(previousSecurity);
        }
    }
    public static void fail(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
