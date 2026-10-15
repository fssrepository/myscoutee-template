package com.raxim.myscoutee.common.security.media;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.raxim.myscoutee.domain.accounts.api.FirebaseCredentialMarker;
import com.raxim.myscoutee.domain.accounts.api.AuthenticatedActor;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.accounts.api.AccountMediaSessions;
import com.raxim.myscoutee.domain.media.api.MediaSessionAccess;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Media-only credential scoped to the authenticated app session and its database. */
public class MediaSessionAccessService implements MediaSessionAccess {
    private static final String COOKIE = "myscoutee_media_session";
    private static final String DEMO_PREFIX = "demo.";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountMediaSessions sessions;

    public MediaSessionAccessService(AccountMediaSessions sessions) {
        this.sessions = sessions;
    }

    public Authentication authenticate(HttpServletRequest request) {
        String value = cookie(request);
        if (sessionMode(request) != MongoSessionModeContext.currentMode()) return null;
        String credential = value.startsWith(DEMO_PREFIX) ? value.substring(DEMO_PREFIX.length()) : value;
        int separator = credential.indexOf('.');
        if (separator < 1 || value.length() > 512 || !credential.substring(separator + 1).matches("[0-9a-f]{64}")) return null;
        String userId;
        try {
            userId = new String(Base64.getUrlDecoder().decode(credential.substring(0, separator)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        // The primary-key lookup stays bounded by the user's two active sessions.
        return sessions.authenticateMediaToken(userId, hash(value))
                .map(profileId -> UsernamePasswordAuthenticationToken.authenticated(profileId, null,
                        List.of(new SimpleGrantedAuthority("MEDIA"))))
                .orElse(null);
    }

    public void issueForProfileRead(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (!"GET".equals(request.getMethod()) || !request.getRequestURI().endsWith("/auth/me")
                || authentication == null || !authentication.isAuthenticated()
                || (MongoSessionModeContext.currentMode() != MongoSessionMode.DEMO
                    && !FirebaseCredentialMarker.isPresent(authentication))
                || !(authentication.getPrincipal() instanceof AuthenticatedActor principal)) return;
        String sessionId = request.getHeader(AccountMediaSessions.SESSION_ID_HEADER);
        if (sessionId == null || sessionId.isBlank()) return;
        var active = sessions.mediaToken(principal.getUserId(), sessionId).orElse(null);
        if (active == null) return;
        String existing = cookie(request);
        if (!existing.isEmpty() && sessionMode(request) == MongoSessionModeContext.currentMode()
                && hash(existing).equals(active.hash())) return;
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = (MongoSessionModeContext.currentMode() == MongoSessionMode.DEMO ? DEMO_PREFIX : "")
                + Base64.getUrlEncoder().withoutPadding().encodeToString(principal.getUserId().getBytes(StandardCharsets.UTF_8))
                + "." + HexFormat.of().formatHex(bytes);
        if (!sessions.replaceMediaToken(principal.getUserId(), sessionId, hash(value))) return;
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value)
                .httpOnly(true).secure(request.isSecure()).sameSite("Strict").path("/").build().toString());
    }

    // The prefix selects a database, never authority. Authentication verifies the
    // hash of the entire credential (including this prefix) in that database.
    public static MongoSessionMode sessionMode(HttpServletRequest request) {
        return cookie(request).startsWith(DEMO_PREFIX) ? MongoSessionMode.DEMO : MongoSessionMode.REAL;
    }

    private static String cookie(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return "";
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
