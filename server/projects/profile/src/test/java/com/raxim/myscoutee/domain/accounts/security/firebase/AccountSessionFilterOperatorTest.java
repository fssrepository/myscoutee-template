package com.raxim.myscoutee.domain.accounts.security.firebase;

import com.raxim.myscoutee.domain.accounts.security.session.AccountSessionFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService;

import jakarta.servlet.FilterChain;

@org.junit.jupiter.api.Tag("standard")
class AccountSessionFilterOperatorTest {
    @Test void verifiedIntegrationClientDoesNotNeedItsOwnersBrowserSession() throws Exception {
        var sessions = mock(UserLoginSessionService.class);
        when(sessions.isCurrentUserEnrolled()).thenReturn(true);
        var principal = new FirebasePrincipal("owner", null, "INTEGRATION");
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authentication.setDetails(org.mockito.Mockito.mock(com.raxim.myscoutee.domain.integrations.api.IntegrationClientIdentity.class));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var filter = new AccountSessionFilter(mock(FirebaseService.class), new ConfigProperties(),
                sessions, mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        var request = new MockHttpServletRequest("POST", "/integrations/v1/connect");
        var response = new MockHttpServletResponse();var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200); verify(chain).doFilter(request, response);
        verify(sessions, never()).isCurrentUserEnrolled();
        verify(sessions, never()).isCurrentSessionActive(org.mockito.ArgumentMatchers.any());
    }

    @Test void apiPathOrClaimedRoleAloneCannotBypassAnEnrolledBrowserSession() throws Exception {
        for (String role : List.of("U", "INTEGRATION")) {
            var sessions = mock(UserLoginSessionService.class);
            when(sessions.isCurrentUserEnrolled()).thenReturn(true);
            var principal = new FirebasePrincipal("owner", null, role);
            SecurityContextHolder.getContext().setAuthentication(new FirebaseAuthenticationToken(principal, null, principal.getAuthorities()));
            var filter = new AccountSessionFilter(mock(FirebaseService.class), new ConfigProperties(),
                    sessions, mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
            var response = new MockHttpServletResponse();var chain = mock(FilterChain.class);
            filter.doFilter(new MockHttpServletRequest("POST", "/integrations/v1/connect"), response, chain);
            assertThat(response.getStatus()).isEqualTo(401);
            org.mockito.Mockito.verifyNoInteractions(chain);
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicExploreOperatorApiFailsClosedWithoutSelectedDemoUserHeader() throws Exception {
        FirebaseService firebaseService = mock(FirebaseService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(true);
        UserLoginSessionService sessionService = mock(UserLoginSessionService.class);
        AccountSessionFilter filter = new AccountSessionFilter(
                firebaseService,
                properties,
                sessionService,
                mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        MockHttpServletRequest request = demoOperatorRequest();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(firebaseService, never()).loadDemoOperatorById(org.mockito.ArgumentMatchers.any());
        verify(sessionService, never()).isCurrentUserEnrolled();
        verify(chain).doFilter(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anonymousPublicHealthRequestDoesNotRequireAnApplicationSession()
            throws Exception {
        FirebaseService firebaseService = mock(FirebaseService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(true);
        UserLoginSessionService sessionService = mock(UserLoginSessionService.class);
        AccountSessionFilter filter = new AccountSessionFilter(
                firebaseService,
                properties,
                sessionService,
                mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        verify(sessionService, never()).isCurrentUserEnrolled();
        verify(sessionService, never()).isCurrentSessionActive(
                org.mockito.ArgumentMatchers.any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void publicFirebaseConfigurationRemainsReachableWithAnExpiredAppSession()
            throws Exception {
        for (String path : List.of(
                "/deployment/configuration/firebase-config",
                "/api/deployment/configuration/firebase-config")) {
            UserLoginSessionService sessionService = mock(UserLoginSessionService.class);
            when(sessionService.isCurrentUserEnrolled()).thenReturn(true);
            FirebasePrincipal principal = new FirebasePrincipal(
                    "member", "member@example.invalid", List.of("U"), null);
            SecurityContextHolder.getContext().setAuthentication(
                    new FirebaseAuthenticationToken(principal, null, principal.getAuthorities()));
            AccountSessionFilter filter = new AccountSessionFilter(
                    mock(FirebaseService.class),
                    new ConfigProperties(),
                    sessionService,
                    mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            verify(sessionService, never()).isCurrentUserEnrolled();
            verify(chain).doFilter(request, response);

            // The public exception must not admit adjacent configuration paths.
            MockHttpServletRequest protectedRequest =
                    new MockHttpServletRequest("GET", path + "/credentials");
            MockHttpServletResponse protectedResponse = new MockHttpServletResponse();
            FilterChain protectedChain = mock(FilterChain.class);
            filter.doFilter(protectedRequest, protectedResponse, protectedChain);
            assertThat(protectedResponse.getStatus()).isEqualTo(401);
            verify(protectedChain, never()).doFilter(protectedRequest, protectedResponse);
        }
    }

    @Test
    void publicHealthRequestSkipsMemberFallbackWhenFirebaseIsDisabled()
            throws Exception {
        FirebaseService firebaseService = mock(FirebaseService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        properties.setMemberUser("member1@myscoutee.local");
        UserLoginSessionService sessionService = mock(UserLoginSessionService.class);
        when(sessionService.isCurrentUserEnrolled()).thenReturn(true);
        AccountSessionFilter filter = new AccountSessionFilter(
                firebaseService,
                properties,
                sessionService,
                mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(firebaseService, never()).loadUserByUsername(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sessionService, never()).isCurrentUserEnrolled();
        verify(chain).doFilter(request, response);
    }

    @Test
    void publicMediaRequestSkipsMemberFallbackWhenFirebaseIsDisabled()
            throws Exception {
        FirebaseService firebaseService = mock(FirebaseService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        properties.setMemberUser("member1@myscoutee.local");
        UserLoginSessionService sessionService = mock(UserLoginSessionService.class);
        when(sessionService.isCurrentUserEnrolled()).thenReturn(true);
        AccountSessionFilter filter = new AccountSessionFilter(
                firebaseService,
                properties,
                sessionService,
                mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/media/public/help/e2e-small.webp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(firebaseService, never()).loadUserByUsername(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        verify(sessionService, never()).isCurrentUserEnrolled();
        verify(sessionService, never()).isCurrentSessionActive(
                org.mockito.ArgumentMatchers.any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void selectedStoredDemoOperatorGetsOnlyServerDerivedAuthorities() throws Exception {
        FirebaseService firebaseService = mock(FirebaseService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(true);
        Users user = new Users();
        user.setUserId("operator-demo-dev");
        user.setOperator(true);
        FirebasePrincipal principal = new FirebasePrincipal(
                "operator-demo-dev",
                "operator@myscoutee.local",
                List.of("U", "O"),
                user);
        when(firebaseService.loadUserById("operator-demo-dev")).thenReturn(principal);
        AccountSessionFilter filter = new AccountSessionFilter(
                firebaseService,
                properties,
                mock(UserLoginSessionService.class),
                mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        MockHttpServletRequest request = demoOperatorRequest();
        request.addHeader("X-Demo-User-Id", "operator-demo-dev");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("U", "O");
    }

    @Test
    void existingBootstrapAuthenticationIsNeverReplacedByDemoFallback()
            throws Exception {
        FirebaseService firebaseService = mock(FirebaseService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        FirebasePrincipal principal = new FirebasePrincipal(
                "real-operator",
                "operator@deployment.invalid",
                List.of("U", "O"),
                null);
        FirebaseAuthenticationToken bootstrapAuthentication =
                new FirebaseAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(
                bootstrapAuthentication);
        AccountSessionFilter filter = new AccountSessionFilter(
                firebaseService,
                properties,
                mock(UserLoginSessionService.class),
                mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        MockHttpServletRequest request = demoOperatorRequest();
        request.addHeader("X-Demo-User-Id", "operator-demo-dev");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                chain);

        assertThat(SecurityContextHolder.getContext()
                .getAuthentication()).isSameAs(
                        bootstrapAuthentication);
        verify(firebaseService, never())
                .loadDemoOperatorById(
                        org.mockito.ArgumentMatchers.anyString());
        verify(firebaseService, never())
                .loadUserByUsername(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void selectedDemoMemberHeaderAuthenticatesBodyOnlyApiRequests()
            throws Exception {
        FirebaseService firebaseService = mock(FirebaseService.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(false);
        Users user = new Users();
        user.setUserId("nova-social");
        FirebasePrincipal principal = new FirebasePrincipal(
                "nova-social",
                "nova@myscoutee.local",
                List.of("U"),
                user);
        when(firebaseService.loadUserById("nova-social")).thenReturn(principal);
        AccountSessionFilter filter = new AccountSessionFilter(
                firebaseService,
                properties,
                mock(UserLoginSessionService.class),
                mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/game-cards/query");
        request.addHeader(
                DemoSessionRequestSupport.SESSION_KIND_HEADER,
                "demo");
        request.addHeader("X-Demo-User-Id", "nova-social");

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()
                .getName()).isEqualTo("nova@myscoutee.local");
        verify(firebaseService).loadUserById("nova-social");
        verify(firebaseService, never()).loadUserByUsername(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sessionLoginDefersCredentialVerificationToLoginOwner()
            throws Exception {
        FirebaseService firebaseService = mock(FirebaseService.class);
        FirebaseTokenVerifier verifier = mock(FirebaseTokenVerifier.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(true);
        AccountSessionFilter filter = new AccountSessionFilter(
                firebaseService,
                properties,
                mock(UserLoginSessionService.class),
                mock(com.raxim.myscoutee.common.security.media.MediaSessionAccessService.class));
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/api/auth/session/login");
        request.addHeader("Authorization", "Bearer expired-firebase-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
        verify(verifier, never()).verify(
                org.mockito.ArgumentMatchers.anyString());
        verify(firebaseService, never()).loadUserByCredential(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest demoOperatorRequest() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/operator/registry");
        request.addHeader(DemoSessionRequestSupport.SESSION_KIND_HEADER, "demo");
        return request;
    }
}
