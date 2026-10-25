package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.accounts.document.UserLoginAudit;
import com.raxim.myscoutee.domain.accounts.document.UserSessionState;
import com.raxim.myscoutee.domain.accounts.document.UserSessionState.ActiveSession;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserSessionLoginRequestDto;
import com.raxim.myscoutee.domain.accounts.repository.UserLoginAuditsRepository;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;

@org.junit.jupiter.api.Tag("standard")
class UserLoginSessionServiceTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final UserLoginAuditsRepository audits = mock(UserLoginAuditsRepository.class);
    private final NotificationDelivery outbox = mock(NotificationDelivery.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void authenticate() {
        var principal = new FirebasePrincipal("user", "user@example.test", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(audits.findFirstByAttemptId(anyString())).thenReturn(Optional.empty());
        when(audits.save(any(UserLoginAudit.class))).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledLimitAdmitsThirdSessionWithoutRevokingOthers() {
        when(mongo.findById("user", UserSessionState.class)).thenReturn(state("a", "b"));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserSessionState.class))).thenAnswer(call -> {
                    Query query = call.getArgument(0);
                    assertThat(query.getQueryObject().toJson()).doesNotContain("activeSessions.1");
                    return state("a", "b", "c");
                });
        var result = service(-1).registerLogin("c", login("attempt-c"), request);
        assertThat(result.accepted()).isTrue();
        assertThat(result.outcome()).isEqualTo("ACCEPTED");
        assertThat(result.activeSessionCount()).isEqualTo(3);
        assertThat(result.maxActiveSessions()).isEqualTo(-1);
        verify(outbox).enqueueOnce(eq("security-login:attempt-c"), argThat(intent -> intent.body().equals("New login from Browser on unknown OS desktop.")));
        verify(mongo).updateFirst(argThat(q -> q.getQueryObject().getString("_id").equals("user")),
                argThat(u -> u.getUpdateObject().containsKey("$pull")), eq(UserSessionState.class));
    }

    @Test
    void enabledLimitStillBlocksThirdSession() {
        when(mongo.findById("user", UserSessionState.class)).thenReturn(state("a", "b"));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserSessionState.class))).thenAnswer(call -> {
                    Query query = call.getArgument(0);
                    assertThat(query.getQueryObject().toJson()).contains("activeSessions.1");
                    return null;
                });
        var result = service(2).registerLogin("c", login("attempt-c"), request);
        assertThat(result.accepted()).isFalse();
        assertThat(result.outcome()).isEqualTo("BLOCKED_LIMIT");
        assertThat(result.activeSessionCount()).isEqualTo(2);
        assertThat(result.maxActiveSessions()).isEqualTo(2);
        verify(outbox).enqueueOnce(eq("security-login:attempt-c"), any());
    }

    @Test
    void configuredLimitUsesTheChosenMaximumInsteadOfTwo() {
        when(mongo.findById("user", UserSessionState.class)).thenReturn(state("a", "b", "c"));
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserSessionState.class))).thenAnswer(call -> {
                    Query query = call.getArgument(0);
                    assertThat(query.getQueryObject().toJson()).contains("activeSessions.2");
                    return null;
                });
        var result = service(3).registerLogin("d", login("attempt-d"), request);
        assertThat(result.accepted()).isFalse();
        assertThat(result.maxActiveSessions()).isEqualTo(3);
    }

    @Test
    void invalidLimitsAreRejectedRatherThanBlockingAllLogins() {
        assertThatThrownBy(() -> service(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(-2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reopeningExistingSessionDoesNotAddAnotherSessionOrWarning() {
        when(mongo.findById("user", UserSessionState.class)).thenReturn(state("a", "b", "c"));
        var result = service(-1).registerLogin("c", login("attempt-reopen"), request);
        assertThat(result.accepted()).isTrue();
        assertThat(result.existingSession()).isTrue();
        assertThat(result.activeSessionCount()).isEqualTo(3);
        verify(mongo, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(UserSessionState.class));
        verifyNoInteractions(outbox);
    }

    @Test
    void disabledLimitDoesNotAcceptAnUnregisteredSessionAfterAWriteRace() {
        when(mongo.findById("user", UserSessionState.class)).thenReturn(state("a", "b"));
        assertThatThrownBy(() -> service(-1).registerLogin("c", login("attempt-c"), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
        verify(audits, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void disablingQuotaDoesNotAllowUnauthenticatedRegistration() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service(-1).registerLogin("c", login("attempt-c"), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(mongo, audits, outbox);
    }

    private UserLoginSessionService service(int maxActiveSessions) {
        return new UserLoginSessionService(mongo, audits, outbox, maxActiveSessions);
    }

    private UserSessionLoginRequestDto login(String attempt) {
        return new UserSessionLoginRequestDto(attempt, "demo", null, null);
    }

    private UserSessionState state(String... ids) {
        var state = new UserSessionState();
        state.setId("user");
        for (String id : ids) {
            var session = new ActiveSession();
            session.setSessionId(id);
            state.getActiveSessions().add(session);
        }
        return state;
    }
}
