package com.raxim.myscoutee.domain.integrations.config;

import com.raxim.myscoutee.domain.integrations.service.AdminClientAccessService;
import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Tag("standard")
class AdminClientAuthenticationFilterTest {
    final IntegrationTokenService tokens = mock(IntegrationTokenService.class);
    final AdminClientAccessService access = mock(AdminClientAccessService.class);
    final AdminClientAuthenticationFilter filter = new AdminClientAuthenticationFilter(tokens, access);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("GET", "/admin-client/v1/overview");
        request.addHeader("Authorization", "Bearer msc_key"); request.addHeader("X-MyScoutee-Client-Id", "client");
        return request;
    }
    @Test void absentOrWrongScopeTokenCannotReachPrivateController() throws Exception {
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(request(), response, chain); assertEquals(401, response.getStatus()); assertNull(chain.getRequest());
        verify(tokens).authenticateClient(eq("msc_key"), eq("client"), anyString(), eq("admin-client"));
    }
    @Test void onlyReadOnlyClientAuthorityIsGrantedAndDemotionIsEffectiveOnNextPoll() throws Exception {
        when(tokens.authenticateClient(any(), any(), any(), eq("admin-client")))
            .thenReturn(new IntegrationTokenService.AuthenticatedIntegrationClient("admin", "token", "client"));
        when(access.requireAdmin("admin")).thenReturn(new com.raxim.myscoutee.domain.accounts.document.Users());
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(request(), response, chain); assertNotNull(chain.getRequest());
        assertEquals("[ADMIN_CLIENT]", SecurityContextHolder.getContext().getAuthentication().getAuthorities().toString());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireAdmin("admin");
        response = new MockHttpServletResponse(); chain = new MockFilterChain();
        filter.doFilter(request(), response, chain); assertEquals(403, response.getStatus()); assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
