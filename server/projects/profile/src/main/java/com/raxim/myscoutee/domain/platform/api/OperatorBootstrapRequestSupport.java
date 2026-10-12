package com.raxim.myscoutee.domain.platform.api;

import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

public final class OperatorBootstrapRequestSupport {
    public static final String LOGIN_PATH = "/auth/operator-bootstrap";
    public static final String API_LOGIN_PATH = "/api/auth/operator-bootstrap";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String AUTHORIZATION_SCHEME = "OperatorBootstrap";

    private OperatorBootstrapRequestSupport() {
    }

    public static boolean isLoginRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String requestUri = request.getRequestURI();
        if (!StringUtils.hasText(requestUri)) {
            return false;
        }
        String normalized = requestUri.trim();
        return LOGIN_PATH.equals(normalized)
                || API_LOGIN_PATH.equals(normalized);
    }

    public static boolean hasAuthorizationHeader(
            HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization)) {
            return false;
        }
        String normalized = authorization.trim();
        return AUTHORIZATION_SCHEME.equalsIgnoreCase(normalized)
                || (normalized.length() > AUTHORIZATION_SCHEME.length()
                        && AUTHORIZATION_SCHEME.equalsIgnoreCase(
                                normalized.substring(
                                        0,
                                        AUTHORIZATION_SCHEME.length()))
                        && Character.isWhitespace(
                                normalized.charAt(
                                        AUTHORIZATION_SCHEME.length())));
    }

    public static String extractToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String normalized = authorization.trim();
        if (AUTHORIZATION_SCHEME.equalsIgnoreCase(normalized)) {
            return "";
        }
        if (normalized.length() <= AUTHORIZATION_SCHEME.length()
                || !AUTHORIZATION_SCHEME.equalsIgnoreCase(
                        normalized.substring(
                                0,
                                AUTHORIZATION_SCHEME.length()))
                || !Character.isWhitespace(
                        normalized.charAt(AUTHORIZATION_SCHEME.length()))) {
            return null;
        }
        String token = normalized.substring(
                AUTHORIZATION_SCHEME.length() + 1).trim();
        return StringUtils.hasText(token) ? token : "";
    }
}
