package com.raxim.myscoutee.domain.accounts.security.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;

import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebaseCredential;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.platform.service.AppSettingsService;

@org.junit.jupiter.api.Tag("standard")
class FirebaseServiceTest {
    @Test
    void linkedGoogleAndFacebookCredentialsKeepTheExistingProfile() {
        UsersRepository repository = mock(UsersRepository.class);
        AppSettingsService settings = mock(AppSettingsService.class);
        Users existing = new Users();
        existing.setId("existing-profile");
        existing.setUserId("existing-profile");
        existing.setEmail("person@example.test");
        existing.setFirebaseUid("linked-firebase-uid");
        existing.setGoogleId("google-subject");
        when(repository.findFirstByEmail("person@example.test")).thenReturn(Optional.of(existing));
        when(repository.findFirstByFirebaseUid("linked-firebase-uid")).thenReturn(Optional.of(existing));
        when(repository.findFirstByGoogleId("google-subject")).thenReturn(Optional.of(existing));
        when(repository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FirebaseService service = new FirebaseService(new ConfigProperties(), repository, settings);
        FirebaseCredential google = mock(FirebaseCredential.class);
        when(google.getEmail()).thenReturn("Person@Example.Test");
        when(google.getUid()).thenReturn("linked-firebase-uid");
        when(google.getGoogleId()).thenReturn("google-subject");
        when(google.isEmailVerified()).thenReturn(true);
        FirebaseCredential facebook = mock(FirebaseCredential.class);
        when(facebook.getEmail()).thenReturn("person@example.test");
        when(facebook.getUid()).thenReturn("linked-firebase-uid");
        when(facebook.getFacebookId()).thenReturn("facebook-subject");
        when(facebook.isEmailVerified()).thenReturn(true);

        FirebasePrincipal first = (FirebasePrincipal) service.loadUserByCredential(google, null, "/auth/me");
        FirebasePrincipal second = (FirebasePrincipal) service.loadUserByCredential(facebook, null, "/auth/me");

        assertThat(first.getUserId()).isEqualTo("existing-profile");
        assertThat(second.getUserId()).isEqualTo(first.getUserId());
        assertThat(second.getUser()).isSameAs(existing);
        assertThat(existing.getGoogleId()).isEqualTo("google-subject");
        assertThat(existing.getFacebookId()).isEqualTo("facebook-subject");
        verify(repository).save(existing);
    }

    @Test
    void refusesConflictingEmailAndProviderProfilesInsteadOfMergingThem() {
        UsersRepository repository = mock(UsersRepository.class);
        Users emailProfile = new Users();
        emailProfile.setId("email-profile");
        Users providerProfile = new Users();
        providerProfile.setId("other-profile");
        FirebaseCredential credential = mock(FirebaseCredential.class);
        when(credential.getEmail()).thenReturn("person@example.test");
        when(credential.getGoogleId()).thenReturn("google-subject");
        when(credential.isEmailVerified()).thenReturn(true);
        when(repository.findFirstByEmail("person@example.test")).thenReturn(Optional.of(emailProfile));
        when(repository.findFirstByGoogleId("google-subject")).thenReturn(Optional.of(providerProfile));
        FirebaseService service = new FirebaseService(new ConfigProperties(), repository, mock(AppSettingsService.class));

        assertThatThrownBy(() -> service.loadUserByCredential(credential, null, "/auth/me"))
                .isInstanceOf(com.raxim.myscoutee.domain.accounts.security.firebase.exception.FirebaseTokenInvalidException.class);
        verify(repository, never()).save(any(Users.class));
    }

    @Test
    void createsOnboardingUserForNewNormalFirebaseLogin() {
        ConfigProperties configProperties = mock(ConfigProperties.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        FirebaseCredential credential = mock(FirebaseCredential.class);

        String email = "new.person@example.com";
        String expectedUserId = stableUserId(email);
        when(credential.getEmail()).thenReturn("New.Person@Example.com");
        when(credential.getName()).thenReturn("");
        when(credential.isEmailVerified()).thenReturn(true);
        when(appSettingsService.isAdminEmail(email)).thenReturn(false);
        when(usersRepository.findFirstByEmail(email)).thenReturn(Optional.empty());
        when(usersRepository.findByUserId(expectedUserId)).thenReturn(Optional.empty());
        when(usersRepository.findById(expectedUserId)).thenReturn(Optional.empty());
        when(usersRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FirebaseService service = new FirebaseService(configProperties, usersRepository, appSettingsService);

        UserDetails userDetails = service.loadUserByCredential(credential, null, "/auth/me");

        assertThat(userDetails).isInstanceOf(FirebasePrincipal.class);
        FirebasePrincipal principal = (FirebasePrincipal) userDetails;
        assertThat(principal.getUserId()).isEqualTo(expectedUserId);
        assertThat(principal.getEmail()).isEqualTo(email);
        assertThat(principal.isEmailVerified()).isTrue();
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("U");

        verify(usersRepository).save(any(Users.class));
        Users created = principal.getUser();
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(expectedUserId);
        assertThat(created.getEmail()).isEqualTo(email);
        assertThat(created.getProfileStatus()).isEqualTo("onboarding");
        assertThat(created.getStatus()).isEqualTo("A");
        assertThat(created.getProfileFormVersion()).isZero();
        assertThat(created.getImages()).isEmpty();
        assertThat(created.getInitials()).isEqualTo("NP");
        assertThat(created.getDeletedAtIso()).isNotBlank();
    }

    @Test
    void legacyAndNonFirebasePrincipalsDefaultToUnverifiedEmail() {
        FirebasePrincipal legacy = new FirebasePrincipal(
                "legacy-user",
                "legacy@example.test",
                "U");

        assertThat(legacy.isEmailVerified()).isFalse();
    }

    @Test
    void doesNotCreateOnboardingUserForAdminLogin() {
        ConfigProperties configProperties = mock(ConfigProperties.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        FirebaseCredential credential = mock(FirebaseCredential.class);

        String email = "admin@example.com";
        String expectedUserId = stableUserId(email);
        when(credential.getEmail()).thenReturn(email);
        when(appSettingsService.isAdminEmail(email)).thenReturn(true);
        when(usersRepository.findFirstByEmail(email)).thenReturn(Optional.empty());
        when(usersRepository.findByUserId(expectedUserId)).thenReturn(Optional.empty());
        when(usersRepository.findById(expectedUserId)).thenReturn(Optional.empty());

        FirebaseService service = new FirebaseService(configProperties, usersRepository, appSettingsService);

        UserDetails userDetails = service.loadUserByCredential(credential, null, "/admin");

        assertThat(userDetails).isInstanceOf(FirebasePrincipal.class);
        FirebasePrincipal principal = (FirebasePrincipal) userDetails;
        assertThat(principal.getUserId()).isEqualTo(expectedUserId);
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("A");
        assertThat(principal.getUser()).isNull();
        verify(usersRepository, never()).save(any(Users.class));
    }

    @Test
    void adminAuthorityComesOnlyFromDeploymentAppSettings() {
        ConfigProperties configProperties = new ConfigProperties();
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        FirebaseCredential credential = mock(FirebaseCredential.class);
        String email = "former-runtime-admin@example.com";
        String expectedUserId = stableUserId(email);
        when(credential.getEmail()).thenReturn(email);
        when(appSettingsService.isAdminEmail(email)).thenReturn(false);
        when(usersRepository.findFirstByEmail(email))
                .thenReturn(Optional.empty());
        when(usersRepository.findByUserId(expectedUserId))
                .thenReturn(Optional.empty());
        when(usersRepository.findById(expectedUserId))
                .thenReturn(Optional.empty());
        when(usersRepository.save(any(Users.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FirebasePrincipal principal = (FirebasePrincipal) new FirebaseService(
                configProperties,
                usersRepository,
                appSettingsService)
                .loadUserByCredential(credential, null, "/admin");

        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("U");
        assertThat(principal.getUser()).isNotNull();
        assertThat(java.util.Arrays.stream(
                        ConfigProperties.class.getMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.contains("AdminUser"));
    }

    @Test
    void devLookupKeepsStoredUniqueEmailAndMemberAuthorityForOrdinaryStoredUser() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setMemberUser("member@myscoutee.local");
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        Users ordinary = new Users();
        ordinary.setUserId("ordinary");
        ordinary.setEmail("stored.admin-looking@example.com");
        ordinary.setHostTier("Operator");
        when(usersRepository.findByUserId("ordinary")).thenReturn(Optional.of(ordinary));

        FirebasePrincipal principal = (FirebasePrincipal) new FirebaseService(
                configProperties,
                usersRepository,
                appSettingsService)
                .loadUserById("ordinary");

        assertThat(principal.getEmail()).isEqualTo("stored.admin-looking@example.com");
        assertThat(principal.getAuthorities()).extracting(Object::toString)
                .containsExactly("U");
    }

    @Test
    void devLookupCreatesStableUniqueEmailForFreshDemoProfile() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setMemberUser("member1@myscoutee.local");
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        String userId = "demo-profile-11111111-2222-3333-4444-555555555555";
        when(usersRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(usersRepository.findById(userId)).thenReturn(Optional.empty());

        FirebaseService service = new FirebaseService(
                configProperties,
                usersRepository,
                appSettingsService);
        FirebasePrincipal first = (FirebasePrincipal) service.loadUserById(userId);
        FirebasePrincipal second = (FirebasePrincipal) service.loadUserById(userId);

        assertThat(first.getEmail())
                .startsWith("member+")
                .endsWith("@myscoutee.local")
                .isEqualTo(second.getEmail())
                .isNotEqualTo("member1@myscoutee.local");
        assertThat(first.getAuthorities()).extracting(Object::toString)
                .containsExactly("U");
    }

    @Test
    void usesFirebaseUidWhenProviderDoesNotReturnEmail() {
        ConfigProperties configProperties = mock(ConfigProperties.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        FirebaseCredential credential = mock(FirebaseCredential.class);

        String fallbackEmail = "facebook-uid-1@firebase.local";
        String expectedUserId = stableUserId(fallbackEmail);
        when(credential.getEmail()).thenReturn(null);
        when(credential.getUid()).thenReturn("facebook-uid-1");
        when(credential.getFacebookId()).thenReturn("facebook-provider-1");
        when(credential.getName()).thenReturn("Facebook User");
        when(appSettingsService.isAdminEmail(fallbackEmail)).thenReturn(false);
        when(usersRepository.findFirstByEmail(fallbackEmail)).thenReturn(Optional.empty());
        when(usersRepository.findByUserId(expectedUserId)).thenReturn(Optional.empty());
        when(usersRepository.findById(expectedUserId)).thenReturn(Optional.empty());
        when(usersRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FirebaseService service = new FirebaseService(configProperties, usersRepository, appSettingsService);

        UserDetails userDetails = service.loadUserByCredential(credential, null, "/auth/me");

        FirebasePrincipal principal = (FirebasePrincipal) userDetails;
        assertThat(principal.getUserId()).isEqualTo(expectedUserId);
        assertThat(principal.getEmail()).isEqualTo(fallbackEmail);
        assertThat(principal.getUser().getEmail()).isEqualTo(fallbackEmail);
        assertThat(principal.getUser().getFirebaseUid()).isEqualTo("facebook-uid-1");
        assertThat(principal.getUser().getFacebookId()).isEqualTo("facebook-provider-1");
        assertThat(principal.getUser().getInitials()).isEqualTo("FU");
    }

    @Test
    void configuredOperatorGetsAdditiveAuthorityAndAnOperatorRecord() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setOperatorUser("operator@example.com");
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        FirebaseCredential credential = mock(FirebaseCredential.class);
        String expectedUserId = stableUserId("operator@example.com");
        when(credential.getEmail()).thenReturn("operator@example.com");
        when(credential.getName()).thenReturn("Configured Operator");
        when(usersRepository.findFirstByEmail("operator@example.com")).thenReturn(Optional.empty());
        when(usersRepository.findByUserId(expectedUserId)).thenReturn(Optional.empty());
        when(usersRepository.findById(expectedUserId)).thenReturn(Optional.empty());
        when(usersRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FirebasePrincipal principal = (FirebasePrincipal) new FirebaseService(
                configProperties,
                usersRepository,
                appSettingsService)
                .loadUserByCredential(credential, null, "/api/operator/registry");

        assertThat(principal.getAuthorities()).extracting(Object::toString)
                .containsExactly("U", "O");
        assertThat(principal.getUser()).isNotNull();
        assertThat(principal.getUser().isOperator()).isTrue();
        assertThat(principal.getUser().getHostTier()).isEqualTo("Operator");
        assertThat(principal.getUser().getProfileStatus()).isEqualTo("public");
    }

    @Test
    void bootstrapCreatesAndReloadsAPersistedOperatorWithoutFirebase() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setOperatorUser(
                "operator-install@deployment.invalid");
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService =
                mock(AppSettingsService.class);
        String email = "operator-install@deployment.invalid";
        String expectedUserId = stableUserId(email);
        when(usersRepository.findFirstByEmail(email))
                .thenReturn(Optional.empty());
        when(usersRepository.findByUserId(expectedUserId))
                .thenReturn(Optional.empty());
        when(usersRepository.findById(expectedUserId))
                .thenReturn(Optional.empty());
        when(usersRepository.save(any(Users.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        FirebaseService service = new FirebaseService(
                configProperties,
                usersRepository,
                appSettingsService);

        FirebasePrincipal created = (FirebasePrincipal)
                service.loadOrCreateBootstrapOperator(email);

        assertThat(created.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("U", "O");
        assertThat(created.getUser()).isNotNull();
        assertThat(created.getUser().isOperator()).isTrue();
        assertThat(created.getUser().getHostTier())
                .isEqualTo("Operator");
        assertThat(created.getUser().getEmail()).isEqualTo(email);
        verify(usersRepository).save(any(Users.class));

        when(usersRepository.findFirstByEmail(email))
                .thenReturn(Optional.of(created.getUser()));
        assertThat(service.loadStoredBootstrapOperator(email))
                .isNotNull();
        assertThat(service.loadStoredBootstrapOperator(
                "other@deployment.invalid")).isNull();
    }

    @Test
    void bootstrapPromotesAnExistingConfiguredUserToOperator() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setOperatorUser(
                "operator-install@deployment.invalid");
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService =
                mock(AppSettingsService.class);
        Users existing = new Users();
        existing.setUserId("legacy-operator");
        existing.setEmail("operator-install@deployment.invalid");
        existing.setOperator(false);
        existing.setHostTier("Member");
        when(usersRepository.findFirstByEmail(
                "operator-install@deployment.invalid"))
                .thenReturn(Optional.of(existing));
        when(usersRepository.save(existing)).thenReturn(existing);
        FirebaseService service = new FirebaseService(
                configProperties,
                usersRepository,
                appSettingsService);

        FirebasePrincipal principal = (FirebasePrincipal)
                service.loadOrCreateBootstrapOperator(
                        "operator-install@deployment.invalid");

        assertThat(existing.isOperator()).isTrue();
        assertThat(existing.getHostTier()).isEqualTo("Operator");
        assertThat(existing.getStatusText())
                .isEqualTo("Operator workspace");
        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("U", "O");
        verify(usersRepository).save(existing);
    }

    @Test
    void demoOperatorLookupFailsClosedForMissingOrOrdinaryUsers() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setOperatorUser("dev@myscoutee.local");
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        Users ordinary = new Users();
        ordinary.setUserId("ordinary");
        ordinary.setEmail("ordinary@example.com");
        when(usersRepository.findByUserId("missing")).thenReturn(Optional.empty());
        when(usersRepository.findById("missing")).thenReturn(Optional.empty());
        when(usersRepository.findByUserId("ordinary")).thenReturn(Optional.of(ordinary));

        FirebaseService service = new FirebaseService(
                configProperties,
                usersRepository,
                appSettingsService);

        assertThat(service.loadDemoOperatorById("missing")).isNull();
        assertThat(service.loadDemoOperatorById("ordinary")).isNull();
    }

    @Test
    void storedOperatorFlagAddsOperatorAuthorityWithoutChangingLegacyBaseRole() {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setMemberUser("member@myscoutee.local");
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        Users operator = new Users();
        operator.setUserId("operator");
        operator.setEmail("operator@example.com");
        operator.setOperator(true);
        when(usersRepository.findByUserId("operator")).thenReturn(Optional.of(operator));

        FirebasePrincipal principal = (FirebasePrincipal) new FirebaseService(
                configProperties,
                usersRepository,
                appSettingsService)
                .loadUserById("operator");

        assertThat(principal.getEmail()).isEqualTo("operator@example.com");
        assertThat(principal.getAuthorities()).extracting(Object::toString)
                .containsExactly("U", "O");
    }

    @Test
    void returnsConcurrentlyCreatedOnboardingUserWhenInsertRaces() {
        ConfigProperties configProperties = mock(ConfigProperties.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        FirebaseCredential credential = mock(FirebaseCredential.class);

        String email = "race.person@example.com";
        String expectedUserId = stableUserId(email);
        Users existing = new Users();
        existing.setUserId(expectedUserId);
        existing.setEmail(email);
        existing.setStatus("A");
        existing.setProfileStatus("onboarding");

        when(credential.getEmail()).thenReturn(email);
        when(credential.getGoogleId()).thenReturn("google-race-id");
        when(appSettingsService.isAdminEmail(email)).thenReturn(false);
        when(usersRepository.findFirstByEmail(email)).thenReturn(Optional.empty());
        when(usersRepository.findByUserId(expectedUserId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(usersRepository.findById(expectedUserId)).thenReturn(Optional.empty());
        when(usersRepository.save(any(Users.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate user"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FirebaseService service = new FirebaseService(configProperties, usersRepository, appSettingsService);

        UserDetails userDetails = service.loadUserByCredential(credential, null, "/auth/me");

        FirebasePrincipal principal = (FirebasePrincipal) userDetails;
        assertThat(principal.getUserId()).isEqualTo(expectedUserId);
        assertThat(principal.getUser()).isSameAs(existing);
        assertThat(existing.getGoogleId()).isEqualTo("google-race-id");
        verify(usersRepository, times(2)).save(any(Users.class));
    }

    private static String stableUserId(String email) {
        return UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
