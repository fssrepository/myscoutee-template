package com.raxim.myscoutee.domain.platform.security.bootstrap;
import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapAuthenticationService;
import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapTokenInvalidException;
import com.raxim.myscoutee.domain.platform.api.OperatorBootstrapRequestSupport;

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

import jakarta.servlet.FilterChain;

@org.junit.jupiter.api.Tag("standard")
class OperatorBootstrapAuthenticationFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyFromTheDedicatedAuthorizationHeader()
            throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        FirebasePrincipal principal = new FirebasePrincipal(
                "operator-id",
                "operator@deployment.invalid",
                List.of("U", "O"),
                null);
        when(service.authenticateToken("signed-token"))
                .thenReturn(principal);
        OperatorBootstrapAuthenticationFilter filter = filter(
                service,
                true);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/operator/configuration");
        request.addHeader(
                "Authorization",
                "OperatorBootstrap signed-token");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                chain);

        assertThat(SecurityContextHolder.getContext()
                .getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("U", "O");
        verify(chain).doFilter(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidOrDemoConflictingBootstrapAuthorization()
            throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        when(service.authenticateToken("invalid"))
                .thenThrow(
                        new OperatorBootstrapTokenInvalidException());
        OperatorBootstrapAuthenticationFilter filter = filter(
                service,
                true);
        MockHttpServletRequest invalid = new MockHttpServletRequest(
                "GET",
                "/operator/configuration");
        invalid.addHeader(
                "Authorization",
                "OperatorBootstrap invalid");
        MockHttpServletResponse invalidResponse =
                new MockHttpServletResponse();
        FilterChain invalidChain = mock(FilterChain.class);

        filter.doFilter(invalid, invalidResponse, invalidChain);

        assertThat(invalidResponse.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext()
                .getAuthentication()).isNull();
        verify(invalidChain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        MockHttpServletRequest conflicting =
                new MockHttpServletRequest(
                        "GET",
                        "/operator/configuration");
        conflicting.addHeader(
                "Authorization",
                "OperatorBootstrap signed-token");
        conflicting.addHeader(
                DemoSessionRequestSupport.SESSION_KIND_HEADER,
                "demo");
        MockHttpServletResponse conflictingResponse =
                new MockHttpServletResponse();
        FilterChain conflictingChain = mock(FilterChain.class);

        filter.doFilter(
                conflicting,
                conflictingResponse,
                conflictingChain);

        assertThat(conflictingResponse.getStatus()).isEqualTo(401);
        verify(service, never()).authenticateToken("signed-token");
        verify(conflictingChain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresQueryTokensAndOtherAuthorizationSchemes()
            throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        OperatorBootstrapAuthenticationFilter filter = filter(
                service,
                true);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/operator/configuration");
        request.setParameter("token", "query-token");
        request.addHeader("Authorization", "Bearer firebase-token");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                chain);

        verify(service, never()).authenticateToken(
                org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleAuthorizationNeverBlocksFreshPasswordLogin()
            throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        OperatorBootstrapAuthenticationFilter filter = filter(
                service,
                true);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                OperatorBootstrapRequestSupport.LOGIN_PATH);
        request.addHeader(
                "Authorization",
                "OperatorBootstrap expired-token");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                chain);

        verify(service, never()).authenticateToken(
                org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsBootstrapBearerTokenOverCleartextTransport()
            throws Exception {
        OperatorBootstrapAuthenticationService service =
                mock(OperatorBootstrapAuthenticationService.class);
        OperatorBootstrapAuthenticationFilter filter = filter(
                service,
                false);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/operator/configuration");
        request.addHeader(
                "Authorization",
                "OperatorBootstrap signed-token");
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(426);
        verify(service, never()).authenticateToken("signed-token");
        verify(chain, never()).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private OperatorBootstrapAuthenticationFilter filter(
            OperatorBootstrapAuthenticationService service,
            boolean allowInsecure) {
        ConfigProperties properties = new ConfigProperties();
        properties.setOperatorBootstrapAllowInsecure(allowInsecure);
        return new OperatorBootstrapAuthenticationFilter(
                service,
                properties, new com.raxim.myscoutee.domain.accounts.service.ProfileAuthenticationService());
    }
}
