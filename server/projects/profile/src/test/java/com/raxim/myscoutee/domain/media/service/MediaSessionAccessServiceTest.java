package com.raxim.myscoutee.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.mongodb.client.result.UpdateResult;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebaseCredential;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.common.security.media.MediaSessionAccessService;
import com.raxim.myscoutee.domain.accounts.document.UserSessionState;
import com.raxim.myscoutee.domain.accounts.service.UserLoginSessionService;

import jakarta.servlet.http.Cookie;

@org.junit.jupiter.api.Tag("standard")
class MediaSessionAccessServiceTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MediaSessionAccessService service = new MediaSessionAccessService(new com.raxim.myscoutee.domain.accounts.service.AccountMediaSessionService(mongo));
    private final UserSessionState.ActiveSession active = new UserSessionState.ActiveSession();

    @AfterEach
    void clearMode() { MongoSessionModeContext.clear(); }

    @Test
    void demoSessionGetsScopedCredentialAndRevocationRejectsIt() throws Exception {
        Cookie cookie = issue(MongoSessionMode.DEMO, null);
        assertThat(cookie.getValue()).startsWith("demo.");
        MockHttpServletRequest read = read(cookie);
        assertThat(MediaSessionAccessService.sessionMode(read)).isEqualTo(MongoSessionMode.DEMO);
        assertThat(service.authenticate(read).getName()).isEqualTo("viewer");
        active.setMediaTokenHash(null);
        assertThat(service.authenticate(read)).isNull();
    }

    @Test
    void changingRealmOrTokenCannotReuseTheIssuedCredential() throws Exception {
        Cookie cookie = issue(MongoSessionMode.DEMO, null);
        MongoSessionModeContext.set(MongoSessionMode.REAL);
        assertThat(service.authenticate(read(cookie))).isNull();
        assertThat(service.authenticate(read(new Cookie(cookie.getName(), cookie.getValue().substring(5))))).isNull();
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        String value = cookie.getValue();
        String changed = value.substring(0, value.length() - 1) + (value.endsWith("0") ? "1" : "0");
        assertThat(service.authenticate(read(new Cookie(cookie.getName(), changed)))).isNull();
    }

    @Test
    void firebaseCredentialRetainsItsRealDatabaseFormat() throws Exception {
        Cookie cookie = issue(MongoSessionMode.REAL, new FirebaseCredential(null));
        assertThat(cookie.getValue()).doesNotStartWith("demo.");
        assertThat(service.authenticate(read(cookie)).getName()).isEqualTo("viewer");
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        assertThat(service.authenticate(read(cookie))).isNull();
        assertThat(service.authenticate(read(new Cookie(cookie.getName(), "demo." + cookie.getValue())))).isNull();
    }

    @Test
    void realModeWithoutFirebaseCannotIssueCredential() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.issueForProfileRead(profileRequest(), response, actor(null));
        assertThat(response.getCookie("myscoutee_media_session")).isNull();
        verifyNoInteractions(mongo);
    }

    @Test
    void absentActiveDemoSessionCannotIssueCredential() {
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.issueForProfileRead(profileRequest(), response, actor(null));
        assertThat(response.getCookie("myscoutee_media_session")).isNull();
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(UserSessionState.class));
    }

    @Test
    void anonymousAndMalformedCookiesAreRejectedWithoutDatabaseReads() {
        assertThat(service.authenticate(read(null))).isNull();
        assertThat(service.authenticate(read(new Cookie("myscoutee_media_session", "invalid")))).isNull();
        assertThat(service.authenticate(read(new Cookie("myscoutee_media_session", "!." + "a".repeat(64))))).isNull();
        verifyNoInteractions(mongo);
    }

    private Cookie issue(MongoSessionMode mode, Object credentials) throws Exception {
        MongoSessionModeContext.set(mode);
        active.setSessionId("session");
        UserSessionState state = new UserSessionState();
        state.setActiveSessions(List.of(active));
        when(mongo.findOne(any(Query.class), eq(UserSessionState.class))).thenReturn(state);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(UserSessionState.class))).thenAnswer(call -> {
            Update update = call.getArgument(1);
            active.setMediaTokenHash(update.getUpdateObject().get("$set", org.bson.Document.class)
                    .getString("activeSessions.$.mediaTokenHash"));
            return UpdateResult.acknowledged(1, 1L, null);
        });
        when(mongo.exists(any(Query.class), eq(UserSessionState.class))).thenAnswer(call -> {
            Query query = call.getArgument(0);
            var filter = query.getQueryObject();
            String hash = filter.get("activeSessions", org.bson.Document.class)
                    .get("$elemMatch", org.bson.Document.class).getString("mediaTokenHash");
            return "viewer".equals(filter.getString("_id")) && hash.equals(active.getMediaTokenHash());
        });
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.issueForProfileRead(profileRequest(), response, actor(credentials));
        Cookie cookie = response.getCookie("myscoutee_media_session");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(active.getMediaTokenHash()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(cookie.getValue().getBytes(StandardCharsets.UTF_8))));
        return cookie;
    }

    private UsernamePasswordAuthenticationToken actor(Object credentials) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new FirebasePrincipal("viewer", "", "U"), credentials, List.of());
    }

    private MockHttpServletRequest profileRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader(UserLoginSessionService.SESSION_ID_HEADER, "session");
        return request;
    }

    private MockHttpServletRequest read(Cookie cookie) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/private");
        if (cookie != null) request.setCookies(cookie);
        return request;
    }
}
