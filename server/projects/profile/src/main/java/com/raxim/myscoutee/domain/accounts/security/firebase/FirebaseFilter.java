package com.raxim.myscoutee.domain.accounts.security.firebase;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.raxim.myscoutee.domain.platform.api.OperatorBootstrapRequestSupport;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebaseCredential;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.accounts.security.firebase.exception.FirebaseTokenInvalidException;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService;
import com.raxim.myscoutee.domain.media.api.MediaSessionAccess;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FirebaseFilter extends OncePerRequestFilter {
    private static final String AUTH_FIREBASE = "X-Authorization-Firebase";
    private static final String AUTH_LINK = "X-Authorization-Link";
    private static final String AUTHORIZATION = "Authorization";
    private static final String DEMO_USER_ID = "X-Demo-User-Id";

    private final FirebaseService firebaseService;
    private final RuntimeConfiguration configProperties;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final UserLoginSessionService userLoginSessionService;
    private final MediaSessionAccess mediaSessionAccess;

    public FirebaseFilter(
            FirebaseService firebaseService,
            RuntimeConfiguration configProperties,
            FirebaseTokenVerifier firebaseTokenVerifier,
            UserLoginSessionService userLoginSessionService,
            MediaSessionAccess mediaSessionAccess) {
        this.firebaseService = firebaseService;
        this.configProperties = configProperties;
        this.firebaseTokenVerifier = firebaseTokenVerifier;
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
        // A fresh local-operator login must not be rejected because the browser
        // still carries an expired Firebase credential from an earlier session.
        if (OperatorBootstrapRequestSupport.isLoginRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (isPublicEntryBootstrapRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication existing =
                SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            // API keys and OAuth grants were verified by the integration filter.
            // They have their own expiry/revocation and never carry a browser
            // session ID, even when their owner has an enrolled app session.
            if (existing.getDetails() instanceof com.raxim.myscoutee.domain.integrations.api.IntegrationClientIdentity
                    && existing.getAuthorities().stream().anyMatch(a -> "INTEGRATION".equals(a.getAuthority()))
                    && request.getRequestURI().matches("/(?:api/)?integrations/v1/.*")) {
                filterChain.doFilter(request, response);
                return;
            }
            continueIfSessionActive(request, response, filterChain);
            return;
        }
        // Demo routing selects a database, not administrator authority. Protected
        // admin routes must still verify Firebase credentials when it is enabled.
        if (DemoSessionRequestSupport.isDemoSessionRequest(request)
                && (!configProperties.isFirebaseEnabled()
                    || DemoSessionRequestSupport.isAllowedDemoSessionRequest(request)
                    || DemoSessionRequestSupport.isOperatorRequest(request))) {
            if (DemoSessionRequestSupport.isOperatorRequest(request)) {
                authenticateDemoOperator(request);
            } else {
                authenticateDemoUser(request);
            }
            continueIfSessionActive(request, response, filterChain);
            return;
        }

        if (!configProperties.isFirebaseEnabled()) {
            authenticateWithoutFirebase(request);
            continueIfSessionActive(request, response, filterChain);
            return;
        }

        String firebaseToken = extractFirebaseToken(request);
        if (firebaseToken == null || firebaseToken.isBlank()) {
            continueIfSessionActive(request, response, filterChain);
            return;
        }

        try {
            FirebaseCredential credential = parseToken(firebaseToken);
            String xLink = request.getHeader(AUTH_LINK);

            UserDetails userDetails = firebaseService.loadUserByCredential(credential, xLink, request.getRequestURI());
            setAuthentication(userDetails, credential);
            continueIfSessionActive(request, response, filterChain);
        } catch (FirebaseTokenInvalidException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Firebase token.");
        }
    }

    public FirebaseCredential parseToken(String firebaseToken) {
        return firebaseTokenVerifier.verify(firebaseToken);
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

    private void authenticateDemoOperator(HttpServletRequest request) {
        String requestedUserId = request == null ? null : request.getHeader(DEMO_USER_ID);
        if (!StringUtils.hasText(requestedUserId)) {
            return;
        }
        UserDetails userDetails = firebaseService.loadDemoOperatorById(requestedUserId.trim());
        if (userDetails == null
                || userDetails.getAuthorities().stream()
                        .noneMatch(authority -> "O".equals(authority.getAuthority()))) {
            return;
        }
        setAuthentication(userDetails, null);
    }

    private void authenticateDemoUser(HttpServletRequest request) {
        String requestedUserId = request == null ? null : request.getHeader(DEMO_USER_ID);
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

    private String extractFirebaseToken(HttpServletRequest request) {
        String token = request.getHeader(AUTH_FIREBASE);
        if (token != null && !token.isBlank()) {
            return token;
        }
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String queryToken = request.getParameter("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return isWebSocketTokenRequest(request) ? queryToken.trim() : null;
        }
        return null;
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
                            "/api/explanation/active" -> true;
                    default -> false;
                };
        return publicDocumentRead
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
        String requestUri = request == null ? "" : request.getRequestURI();
        return requestUri.endsWith("/auth/session/login");
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
