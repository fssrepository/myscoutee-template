package com.raxim.myscoutee.domain.accounts.security.session;

import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseService;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseAuthenticationToken;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.raxim.myscoutee.domain.accounts.api.SessionRequestSupport;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService;
import com.raxim.myscoutee.domain.media.api.MediaSessionAccess;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AccountSessionFilter extends OncePerRequestFilter {
    private static final String AUTH_LINK = "X-Authorization-Link";
    private static final String DEMO_USER_ID = "X-Demo-User-Id";

    private final FirebaseService firebaseService;
    private final RuntimeConfiguration configProperties;
    private final UserLoginSessionService userLoginSessionService;
    private final MediaSessionAccess mediaSessionAccess;

    public AccountSessionFilter(
            FirebaseService firebaseService,
            RuntimeConfiguration configProperties,
            UserLoginSessionService userLoginSessionService,
            MediaSessionAccess mediaSessionAccess) {
        this.firebaseService = firebaseService;
        this.configProperties = configProperties;
        this.userLoginSessionService = userLoginSessionService;
        this.mediaSessionAccess = mediaSessionAccess;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (MediaSessionAccess.isPrivateRead(request)) {
            Authentication media = mediaSessionAccess.authenticate(request);
            if (media == null || !("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
                response.setHeader("Cache-Control", "no-store");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "An active app session is required.");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(media);
            filterChain.doFilter(request, response);
            return;
        }
        // Credential verification is owned by the common login endpoint. A stale
        // app credential must not block signing in with a different account.
        if (SessionRequestSupport.isLoginRequest(request)) {
            if (DemoSessionRequestSupport.isDemoSessionRequest(request)) authenticateDemoUser(request);
            filterChain.doFilter(request, response);
            return;
        }
        if (isPublicEntryBootstrapRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            if (existing.getDetails() instanceof com.raxim.myscoutee.domain.integrations.api.IntegrationClientIdentity
                    && ((existing.getAuthorities().stream().anyMatch(a -> "INTEGRATION".equals(a.getAuthority()))
                         && request.getRequestURI().matches("/(?:api/)?integrations/v1/.*"))
                        || (existing.getAuthorities().stream().anyMatch(a -> "ADMIN_CLIENT".equals(a.getAuthority()))
                         && request.getRequestURI().matches("/(?:api/)?admin-client/v1/.*")))) {
                filterChain.doFilter(request, response);
                return;
            }
            continueIfSessionActive(request, response, filterChain);
            return;
        }
        if (DemoSessionRequestSupport.isDemoSessionRequest(request)) {
            if (SessionRequestSupport.bearerToken(request) != null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Conflicting app session credentials.");
                return;
            }
            authenticateDemoUser(request);
            continueIfSessionActive(request, response, filterChain);
            return;
        }
        String token = SessionRequestSupport.bearerToken(request);
        if (token != null) {
            AccountSessionCredential session = userLoginSessionService.authenticateAccessToken(token);
            if (session == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "App session is not active.");
                return;
            }
            String requestedSessionId = request.getHeader(UserLoginSessionService.SESSION_ID_HEADER);
            if (!StringUtils.hasText(requestedSessionId) && isWebSocketTokenRequest(request)) {
                requestedSessionId = request.getParameter("sessionId");
            }
            if (StringUtils.hasText(requestedSessionId) && !session.sessionId().equals(requestedSessionId)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "App session identity does not match.");
                return;
            }
            var account = firebaseService.loadUserById(session.userId());
            if (!(account instanceof FirebasePrincipal principal) || principal.getUser() == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is unavailable.");
                return;
            }
            var verified = new FirebasePrincipal(principal.getUserId(), principal.getEmail(),
                    principal.getAuthorities().stream().map(a -> a.getAuthority()).toList(),
                    principal.getUser(), session.emailVerified());
            setAuthentication(verified, session);
            mediaSessionAccess.issueForProfileRead(request, response,
                    SecurityContextHolder.getContext().getAuthentication());
            filterChain.doFilter(request, response);
            return;
        }
        if (!configProperties.isFirebaseEnabled()) authenticateWithoutFirebase(request);
        continueIfSessionActive(request, response, filterChain);
    }

    private void authenticateWithoutFirebase(HttpServletRequest request) {
        // Production leaves member-user empty. Disabling Firebase must not
        // enable authentication by a caller-supplied userId in that deployment.
        if (!configProperties.isMemberFallbackEnabled()) {
            return;
        }
        String requestedUserId = normalizeRequestUserId(request);
        UserDetails userDetails;
        if (StringUtils.hasText(requestedUserId)) {
            userDetails = firebaseService.loadUserById(requestedUserId);
        } else {
            String memberUser = configProperties.getMemberUser();
            if (memberUser == null || memberUser.isBlank()) {
                return;
            }
            userDetails = firebaseService.loadUserByUsername(
                    memberUser,
                    request.getHeader(AUTH_LINK),
                    request.getRequestURI());
        }
        setAuthentication(userDetails, null);
    }

    private void authenticateDemoUser(HttpServletRequest request) {
        if (request == null) return;
        String requestedUserId = request.getHeader(DEMO_USER_ID);
        // Browser WebSockets cannot set custom headers. The existing socket
        // sessionId check below still validates this selected DEMO account.
        if (!StringUtils.hasText(requestedUserId) && isWebSocketTokenRequest(request)) {
            requestedUserId = request.getParameter("userId");
        }
        if (!StringUtils.hasText(requestedUserId)) {
            return;
        }
        setAuthentication(firebaseService.loadUserById(requestedUserId.trim()), null);
    }

    private String normalizeRequestUserId(HttpServletRequest request) {
        String requestedUserId = request.getParameter("userId");
        if (!StringUtils.hasText(requestedUserId)
                && DemoSessionRequestSupport.isDemoSessionRequest(request)) {
            requestedUserId = request.getHeader(DEMO_USER_ID);
        }
        if (!StringUtils.hasText(requestedUserId)) {
            return null;
        }
        String normalizedUserId = requestedUserId.trim();
        return normalizedUserId.isEmpty() ? null : normalizedUserId;
    }

    private boolean isWebSocketTokenRequest(HttpServletRequest request) {
        String requestUri = request == null ? "" : request.getRequestURI();
        return requestUri.endsWith("/activities/chats/ws")
                || requestUri.endsWith("/admin/notifications/ws");
    }

    private boolean isPublicEntryBootstrapRequest(HttpServletRequest request) {
        String requestUri = request == null ? "" : request.getRequestURI();
        // These document reads are already public in WebSecurityConfig. Do not
        // turn an anonymous read into the dev fallback member's expired session.
        boolean publicDocumentRead = request != null
                && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                && switch (requestUri) {
                    case "/help/active", "/privacy/active", "/terms/active", "/explanation/active",
                            "/api/help/active", "/api/privacy/active", "/api/terms/active",
                            "/api/explanation/active", "/auth/facebook/data-deletion/status",
                            "/api/auth/facebook/data-deletion/status" -> true;
                    default -> false;
                };
        // Meta authenticates this callback with signed_request in its owning
        // service; it never has a browser login session or Firebase bearer token.
        boolean signedProviderCallback = request != null && "POST".equals(request.getMethod())
                && (requestUri.equals("/auth/facebook/data-deletion")
                        || requestUri.equals("/api/auth/facebook/data-deletion"));
        return publicDocumentRead || signedProviderCallback
                || requestUri.matches("/(?:api/)?mcp/oauth/(?:metadata|resource|token|register)")
                || requestUri.equals("/health")
                || requestUri.equals("/api/health")
                || requestUri.equals("/media/public")
                || requestUri.startsWith("/media/public/")
                || requestUri.equals("/api/media/public")
                || requestUri.startsWith("/api/media/public/")
                || requestUri.equals("/deployment/configuration")
                || requestUri.equals("/api/deployment/configuration")
                || requestUri.equals("/deployment/configuration/firebase-config")
                || requestUri.equals("/api/deployment/configuration/firebase-config")
                || requestUri.equals("/public/email/unsubscribe")
                || requestUri.equals("/public/email/unsubscribe/status")
                || requestUri.equals("/api/public/email/unsubscribe")
                || requestUri.equals("/api/public/email/unsubscribe/status")
                || requestUri.equals("/i18n/bundle")
                || requestUri.equals("/api/i18n/bundle")
                || requestUri.equals("/auth/eligibility")
                || requestUri.equals("/api/auth/eligibility")
                || requestUri.equals("/auth/demo-users")
                || requestUri.equals("/api/auth/demo-users")
                || requestUri.equals("/landing")
                || requestUri.equals("/api/landing")
                || requestUri.startsWith("/landing/")
                || requestUri.startsWith("/api/landing/");
    }

    private void continueIfSessionActive(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws IOException, ServletException {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof FirebasePrincipal)
                || isSessionLoginRequest(request)
                || userLoginSessionService == null
                || !userLoginSessionService.isCurrentUserEnrolled()) {
            mediaSessionAccess.issueForProfileRead(request, response, authentication);
            filterChain.doFilter(request, response);
            return;
        }
        String sessionId = request.getHeader(UserLoginSessionService.SESSION_ID_HEADER);
        if (!StringUtils.hasText(sessionId) && isWebSocketTokenRequest(request)) {
            sessionId = request.getParameter("sessionId");
        }
        if (userLoginSessionService.isCurrentSessionActive(sessionId)) {
            mediaSessionAccess.issueForProfileRead(request, response, authentication);
            filterChain.doFilter(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "App session is not active.");
    }

    private boolean isSessionLoginRequest(HttpServletRequest request) {
        return SessionRequestSupport.isLoginRequest(request);
    }

    private void setAuthentication(UserDetails userDetails, Object credentials) {
        if (userDetails == null) {
            return;
        }
        Authentication auth = new FirebaseAuthenticationToken(userDetails, credentials,
                userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
