package com.raxim.myscoutee.domain.accounts.service;

import com.raxim.myscoutee.domain.accounts.api.AuthenticatedActor;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseAuthenticationToken;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.Tag("standard")
class SelectedProfileServiceTest {
    private final UsersRepository users = mock(UsersRepository.class);
    private final SelectedProfileService service = new SelectedProfileService(users);

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void firebaseSelectionRefreshesItsOwnProfileWithOneRead() {
        Users cached = user("selected");
        Users current = user("selected");
        authenticate(cached, "selected");
        when(users.findByUserId("selected")).thenReturn(Optional.of(current));

        assertThat(service.resolveCurrentUser("other")).isSameAs(current);
        verify(users).findByUserId("selected");
        verifyNoMoreInteractions(users);
    }

    @Test void firebaseSelectionRetainsItsExistingPrincipalFallback() {
        Users cached = user("selected");
        authenticate(cached, "selected");

        assertThat(service.resolveCurrentUser("other")).isSameAs(cached);
        verify(users).findByUserId("selected");
        verifyNoMoreInteractions(users);
    }

    @Test void missingFirebaseProfileDoesNotSelectTheRequestedAccount() {
        authenticate(null, "missing");

        assertThat(service.resolveSelectedIdentity("other")).isNull();
        verify(users).findByUserId("missing");
        verifyNoMoreInteractions(users);
    }

    @Test void delegatedSelectionPreservesAccountAndWorkspaceConstraints() {
        Users selected = user("selected");
        selected.setAccountUserId("account");
        selected.setWorkspaceGroupId("workspace");
        delegate(new ProfileIdentityDto("selected", "account", "workspace"));
        when(users.findByUserId("selected")).thenReturn(Optional.of(selected));

        assertThat(service.resolveSelectedIdentity("other"))
                .isEqualTo(new ProfileIdentityDto("selected", "account", "workspace"));
        selected.setWorkspaceGroupId("changed");
        assertThat(service.resolveSelectedIdentity("other")).isNull();
        selected.setWorkspaceGroupId("workspace");
        selected.setAccountUserId("changed");
        assertThat(service.resolveSelectedIdentity("other")).isNull();
        verify(users, times(3)).findByUserId("selected");
        verifyNoMoreInteractions(users);
    }

    @Test void mismatchedDelegatedIdentityDoesNotReadAnyProfile() {
        AuthenticatedActor actor = mock(AuthenticatedActor.class);
        when(actor.getUserId()).thenReturn("selected");
        when(actor.getProfileIdentity()).thenReturn(new ProfileIdentityDto("other", null, null));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));

        assertThat(service.resolveCurrentUser("other")).isNull();
        verifyNoInteractions(users);
    }

    @Test void unauthenticatedDelegationDoesNotReadAnyProfile() {
        AuthenticatedActor actor = mock(AuthenticatedActor.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated(actor, null));

        assertThat(service.resolveCurrentUser("other")).isNull();
        verifyNoInteractions(users);
    }

    @Test void nativeUnselectedReadKeepsTheExistingTrimmedRequestLookup() {
        Users selected = user("selected");
        when(users.findByUserId("selected")).thenReturn(Optional.of(selected));

        assertThat(service.resolveSelectedUserId(" selected ")).isEqualTo("selected");
        assertThat(service.resolveSelectedIdentity(" ")).isNull();
        verify(users).findByUserId("selected");
        verifyNoMoreInteractions(users);
    }

    @Test void onboardingMayBeEmptyButAnUninitializedProfileMayNot() {
        Users selected = new Users();
        selected.setUserId("selected");
        when(users.findByUserId("selected")).thenReturn(Optional.of(selected));

        assertThat(service.resolveCurrentUser("selected")).isNull();
        selected.setProfileStatus(" onboarding ");
        assertThat(service.resolveCurrentUser("selected")).isSameAs(selected);
        selected.setProfileStatus("public");
        selected.setImages(List.of(" "));
        assertThat(service.resolveCurrentUser("selected")).isNull();
        selected.setImages(List.of(" /image.webp "));
        assertThat(service.resolveCurrentUser("selected")).isSameAs(selected);
    }

    private void authenticate(Users user, String id) {
        var principal = new FirebasePrincipal(id, null, "U", user);
        SecurityContextHolder.getContext().setAuthentication(
                new FirebaseAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void delegate(ProfileIdentityDto identity) {
        AuthenticatedActor actor = mock(AuthenticatedActor.class);
        when(actor.getUserId()).thenReturn(identity.userId());
        when(actor.getProfileIdentity()).thenReturn(identity);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
    }

    private Users user(String id) {
        Users user = new Users();
        user.setUserId(id);
        user.setName("Member");
        return user;
    }
}
