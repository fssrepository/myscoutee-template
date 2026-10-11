package com.raxim.myscoutee.domain.administration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;
import com.raxim.myscoutee.domain.administration.api.AdministrativeActors;
import com.raxim.myscoutee.domain.platform.api.AdministratorSettings;
import com.raxim.myscoutee.domain.platform.api.AuthenticationMode;

@Tag("standard")
class AdministrativeActorServiceTest {
    private final AuthenticationMode mode = mock(AuthenticationMode.class);
    private final ProfileSummaries profiles = mock(ProfileSummaries.class);
    private final AdministratorSettings settings = mock(AdministratorSettings.class);
    private final AdministrativeActorService service = new AdministrativeActorService(mode, profiles, settings);

    @BeforeEach
    void demoDatabase() { MongoSessionModeContext.set(MongoSessionMode.DEMO); }

    @AfterEach
    void clearContext() {
        MongoSessionModeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void startsWithoutModerationOrCommunityBeansAndPreservesStoredAdminProfile() {
        var ava = mock(ProfileSummary.class);
        when(ava.getName()).thenReturn("Stored admin");
        when(ava.getImages()).thenReturn(List.of("avatar.png"));
        doReturn(Optional.of(ava)).when(profiles).findByUserId("admin-demo-ava");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AuthenticationMode.class, () -> mode);
            context.registerBean(ProfileSummaries.class, () -> profiles);
            context.registerBean(AdministratorSettings.class, () -> settings);
            context.register(AdministrativeActorService.class);
            context.refresh();
            var actor = context.getBean(AdministrativeActors.class).resolveAdminActor("admin-demo-ava");
            assertThat(actor.id()).isEqualTo("admin-demo-ava");
            assertThat(actor.name()).isEqualTo("Stored admin");
            assertThat(actor.images()).containsExactly("avatar.png");
            verify(profiles, times(2)).findByUserId("admin-demo-ava");
            verifyNoMoreInteractions(profiles);
        }
    }

    @Test
    void demoAdminCannotReadRealDatabase() {
        MongoSessionModeContext.set(MongoSessionMode.REAL);
        forbidden(() -> service.resolveAdminActor("admin-demo-ava"));
        verifyNoInteractions(profiles);
    }

    @Test
    void firebaseAdminStillRequiresConfiguredEmail() {
        when(mode.isFirebaseEnabled()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("member@example.test", null, List.of()));
        forbidden(() -> service.resolveAdminActor("admin-demo-ava"));
        verify(settings).isAdminEmail("member@example.test");
        verifyNoInteractions(profiles);
    }

    @Test
    void firebaseIdentitySelectsOnlyItsOwnAccount() {
        when(mode.isFirebaseEnabled()).thenReturn(true);
        when(settings.isAdminEmail("admin@example.test")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin@example.test", null, List.of()));
        var workspace = mock(ProfileSummary.class);
        when(workspace.getAccountUserId()).thenReturn("other@example.test");
        doReturn(Optional.of(workspace)).when(profiles).findByUserId("other-admin-profile");
        forbidden(() -> service.resolveAdminActor("other-admin-profile"));
    }

    @Test
    void standaloneAdminDoesNotNeedACommunityProfile() {
        assertThat(service.resolveAdminActor("admin-demo-ava").id()).isEqualTo("admin-demo-ava");
        verify(profiles, times(2)).findByUserId("admin-demo-ava");
        verifyNoMoreInteractions(profiles);
    }

    @Test
    void ordinaryMemberProfileCannotBecomeAdminBySelection() {
        var workspace = mock(ProfileSummary.class);
        when(workspace.getAccountUserId()).thenReturn("admin-demo-ava");
        when(workspace.getWorkspaceGroupId()).thenReturn("myscoutee-work");
        when(workspace.getHostTier()).thenReturn("Member");
        doReturn(Optional.of(workspace)).when(profiles).findByUserId("ava-member");
        forbidden(() -> service.resolveAdminActor("ava-member"));
    }

    private void forbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
