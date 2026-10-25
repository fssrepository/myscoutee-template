package com.raxim.myscoutee.domain.accounts.security.firebase;

import com.raxim.myscoutee.domain.accounts.security.session.AccountSessionFilter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.common.security.media.MediaSessionAccessService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag("standard")
class AccountSessionFilterWebSocketTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void browserDemoSocketAuthenticatesQueryActorAndChecksItsSession() throws Exception {
        checkSocket("/activities/chats/ws", true);
        SecurityContextHolder.clearContext();
        checkSocket("/admin/notifications/ws", true);
    }

    @Test void revokedSocketSessionIsDenied() throws Exception { checkSocket("/activities/chats/ws", false); }

    void checkSocket(String path, boolean active) throws Exception {
        var accounts = mock(FirebaseService.class);
        var sessions = mock(UserLoginSessionService.class);
        var actor = new FirebasePrincipal("socket-member", null, "U");
        when(accounts.loadUserById("socket-member")).thenReturn(actor);
        when(sessions.isCurrentUserEnrolled()).thenReturn(true);
        when(sessions.isCurrentSessionActive("socket-session")).thenReturn(active);
        var request = new MockHttpServletRequest("GET", path);
        request.addParameter("sessionKind", "demo");
        request.addParameter("userId", "socket-member");
        request.addParameter("sessionId", "socket-session");
        var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        new AccountSessionFilter(accounts, new ConfigProperties(), sessions,
                mock(MediaSessionAccessService.class)).doFilter(request, response, chain);
        assertSame(actor, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(sessions).isCurrentSessionActive("socket-session");
        if (active) verify(chain).doFilter(request, response);
        else { assertEquals(401, response.getStatus()); verifyNoInteractions(chain); }
    }

    @Test void ordinaryDemoHttpRequestCannotSelectAnAccountFromTheSocketQueryFallback() throws Exception {
        var accounts = mock(FirebaseService.class);
        var request = new MockHttpServletRequest("GET", "/activities/chats");
        request.addParameter("sessionKind", "demo"); request.addParameter("userId", "claimed-member");
        new AccountSessionFilter(accounts, new ConfigProperties(),
                mock(UserLoginSessionService.class), mock(MediaSessionAccessService.class))
                .doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        verifyNoInteractions(accounts);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void accessTokenCannotBeCombinedWithAnotherSessionIdOrDemoIdentity() throws Exception {
        var accounts = mock(FirebaseService.class);
        var sessions = mock(UserLoginSessionService.class);
        when(sessions.authenticateAccessToken("app-token")).thenReturn(
                new com.raxim.myscoutee.domain.accounts.security.session.AccountSessionCredential("account", "current", true));
        var filter = new AccountSessionFilter(accounts, new ConfigProperties(), sessions, mock(MediaSessionAccessService.class));
        for (boolean demo : java.util.List.of(false, true)) {
            var request = new MockHttpServletRequest("GET", "/auth/me");
            request.addHeader("Authorization", "Bearer app-token");
            request.addHeader("X-App-Session-Id", "other");
            if (demo) request.addHeader("X-App-Session-Kind", "demo");
            var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            assertEquals(401, response.getStatus()); verifyNoInteractions(chain, accounts);
        }
    }

}
