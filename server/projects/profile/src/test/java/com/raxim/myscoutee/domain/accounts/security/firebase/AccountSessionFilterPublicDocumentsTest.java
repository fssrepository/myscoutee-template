package com.raxim.myscoutee.domain.accounts.security.firebase;

import com.raxim.myscoutee.domain.accounts.security.session.AccountSessionFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.raxim.myscoutee.common.security.media.MediaSessionAccessService;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;

import jakarta.servlet.FilterChain;

@Tag("standard")
class AccountSessionFilterPublicDocumentsTest {
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void evenValidMediaCookieAuthorityCannotAuthorizeUnsafeMethods(String method) throws Exception {
        var media = mock(com.raxim.myscoutee.domain.media.api.MediaSessionAccess.class);
        var authority = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "viewer", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("MEDIA")));
        when(media.authenticate(any())).thenReturn(authority);
        var users = mock(FirebaseService.class);
        var sessions = mock(UserLoginSessionService.class);
        var filter = new AccountSessionFilter(users, new ConfigProperties(), sessions, media);
        var request = new MockHttpServletRequest(method, "/api/media/private");
        request.setCookies(new jakarta.servlet.http.Cookie("myscoutee_media_session", "valid-for-read-only"));
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
        verifyNoInteractions(users, sessions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/help/active", "/privacy/active", "/terms/active", "/explanation/active",
            "/api/help/active", "/api/privacy/active", "/api/terms/active", "/api/explanation/active"})
    void anonymousPublicDocumentDoesNotImpersonateLoggedOutDevMember(String path) throws Exception {
        var users = mock(FirebaseService.class);
        var sessions = mock(UserLoginSessionService.class);
        var properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        properties.setMemberUser("member@example.invalid");
        when(users.loadUserByUsername(anyString(), any(), anyString()))
                .thenReturn(new FirebasePrincipal("member", null, "U"));
        when(sessions.isCurrentUserEnrolled()).thenReturn(true);
        var filter = filter(users, sessions, properties);
        var request = new MockHttpServletRequest("GET", path);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(users, sessions);
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/help/active", "/privacy/active", "/terms/active", "/explanation/active",
            "/api/help/active", "/api/privacy/active", "/api/terms/active", "/api/explanation/active"})
    void publicDocumentRemainsReadableWithExpiredSession(String path) throws Exception {
        var sessions = mock(UserLoginSessionService.class);
        when(sessions.isCurrentUserEnrolled()).thenReturn(true);
        authenticateMember();
        var filter = filter(mock(FirebaseService.class), sessions, new ConfigProperties());
        var request = new MockHttpServletRequest("GET", path);
        request.addHeader(UserLoginSessionService.SESSION_ID_HEADER, "expired-session");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(sessions);
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/privacy/consents", "/api/privacy/consents", "/help/active/credentials",
            "/api/help/active/credentials", "/help/active", "/api/help/active", "/terms/active",
            "/api/terms/active", "/privacy/active", "/api/privacy/active", "/explanation/active",
            "/api/explanation/active", "/auth/facebook/data-deletion/status",
            "/api/auth/facebook/data-deletion/status", "/auth/facebook/data-deletion/credentials",
            "/api/auth/facebook/data-deletion/credentials"})
    void publicReadExceptionDoesNotAdmitConsentWritesOrAdjacentRequests(String path) throws Exception {
        var sessions = mock(UserLoginSessionService.class);
        when(sessions.isCurrentUserEnrolled()).thenReturn(true);
        authenticateMember();
        var filter = filter(mock(FirebaseService.class), sessions, new ConfigProperties());
        var request = new MockHttpServletRequest("POST", path);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/facebook/data-deletion", "/api/auth/facebook/data-deletion",
            "/auth/facebook/data-deletion/status", "/api/auth/facebook/data-deletion/status"})
    void providerDeletionDoesNotRequireAnAppSession(String path) throws Exception {
        var users = mock(FirebaseService.class);
        var sessions = mock(UserLoginSessionService.class);
        var properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        properties.setMemberUser("member@example.invalid");
        when(users.loadUserByUsername(anyString(), any(), anyString()))
                .thenReturn(new FirebasePrincipal("member", null, "U"));
        when(sessions.isCurrentUserEnrolled()).thenReturn(true);
        var request = new MockHttpServletRequest(path.endsWith("/status") ? "GET" : "POST", path);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter(users, sessions, properties).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(users, sessions);
        verify(chain).doFilter(request, response);
    }

    private void authenticateMember() {
        var principal = new FirebasePrincipal("member", null, "U");
        SecurityContextHolder.getContext().setAuthentication(
                new FirebaseAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private AccountSessionFilter filter(FirebaseService users, UserLoginSessionService sessions, ConfigProperties properties) {
        return new AccountSessionFilter(users, properties, sessions,
                mock(MediaSessionAccessService.class));
    }
}
