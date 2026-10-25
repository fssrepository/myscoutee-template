package com.raxim.myscoutee.domain.accounts.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserSessionLoginRequestDto;
import com.raxim.myscoutee.domain.accounts.security.firebase.*;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.*;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.platform.security.bootstrap.BootstrapAccountAuthenticationService;
@Tag("standard")
class AccountLoginServiceTest {
    final BootstrapAccountAuthenticationService passwords = mock(BootstrapAccountAuthenticationService.class);
    final FirebaseTokenVerifier verifier = mock(FirebaseTokenVerifier.class);
    final com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseService accounts = mock(com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseService.class);
    final ConfigProperties config = new ConfigProperties();
    final AccountLoginService login = new AccountLoginService(passwords, verifier, accounts, config);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); MongoSessionModeContext.clear(); }
    @Test void passwordProofIsAcceptedOnlyOverSecureTransport() {
        var account = new FirebasePrincipal("account", "account@example.test", "O");
        when(passwords.authenticate("account@example.test", "secret")).thenReturn(account);
        var proof = new UserSessionLoginRequestDto("attempt", "password", "account@example.test", "secret");
        var request = new MockHttpServletRequest("POST", "/auth/session/login");
        assertThatThrownBy(() -> login.authenticate(proof, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(426));
        verifyNoInteractions(passwords);
        request.setSecure(true); login.authenticate(proof, request);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(account);
    }
    @Test void firebaseProofIsVerifiedOnlyAtTheSharedLoginBoundary() {
        config.setFirebaseEnabled(true);
        var credential = mock(FirebaseCredential.class);
        var account = new FirebasePrincipal("account", "account@example.test", "A");
        when(verifier.verify("identity-proof")).thenReturn(credential);
        when(accounts.loadUserByCredential(credential, null, "/auth/session/login")).thenReturn(account);
        var request = new MockHttpServletRequest("POST", "/auth/session/login"); request.addHeader("Authorization", "Bearer identity-proof");
        login.authenticate(new UserSessionLoginRequestDto("attempt", "google", null, null), request);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(account);
        verifyNoInteractions(passwords);
    }
    @Test void missingProofAndPasswordInDemoRealmAreRejected() {
        var request = new MockHttpServletRequest("POST", "/auth/session/login");
        assertThatThrownBy(() -> login.authenticate(new UserSessionLoginRequestDto("attempt", null, null, null), request))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        assertThatThrownBy(() -> login.authenticate(new UserSessionLoginRequestDto("attempt", "password", "account@example.test", "secret"), request))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(passwords, verifier, accounts);
    }
}
