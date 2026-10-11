package com.raxim.myscoutee.domain.accounts.security.firebase;

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
class FirebaseFilterPublicDocumentsTest {
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

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
            "/api/explanation/active"})
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

    private void authenticateMember() {
        var principal = new FirebasePrincipal("member", null, "U");
        SecurityContextHolder.getContext().setAuthentication(
                new FirebaseAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private FirebaseFilter filter(FirebaseService users, UserLoginSessionService sessions, ConfigProperties properties) {
        return new FirebaseFilter(users, properties, mock(FirebaseTokenVerifier.class), sessions,
                mock(MediaSessionAccessService.class));
    }
}
