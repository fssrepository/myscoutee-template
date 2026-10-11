package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.core.context.SecurityContextHolder;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.*;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.accounts.repository.UserSessionActionsRepository;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.access.config.RegionSecurityProperties;
import com.raxim.myscoutee.domain.platform.api.CountryDirectory;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.notifications.api.NotificationInbox;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;
import com.raxim.myscoutee.common.dto.SharedDtos.LocationCoordinatesDto;

@Tag("standard")
class HomeUserProfileServiceTest {
    private final SelectedProfile selected = mock(SelectedProfile.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final AccessPermissions access = mock(AccessPermissions.class);
    private final CountryDirectory countries = mock(CountryDirectory.class);
    private final RegionSecurityProperties region = new RegionSecurityProperties();
    private final UserSessionActionsRepository actions = mock(UserSessionActionsRepository.class);
    private final OperationalTelemetry telemetry = mock(OperationalTelemetry.class);
    private final ProfileStatusWork statusWork = mock(ProfileStatusWork.class);
    private final NotificationJobs jobs = mock(NotificationJobs.class);
    private final NotificationInbox notifications = mock(NotificationInbox.class);
    private HomeUserProfileService service;

    @BeforeEach void setup() {
        service = new HomeUserProfileService(selected, users, access, countries, region, actions,
                telemetry, statusWork, jobs, notifications);
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void minimalProfileSavePreservesUnexposedStoredFieldsAndCannotGrantPrivileges() {
        Users original = user("member");
        original.setAge(42); original.setHeadline("Retained by a future extension");
        original.setHostTier("Member"); original.setFirebaseUid("private-provider-uid");
        original.setGoogleId("private-google-id"); original.setOperator(false);
        original.setLanguages(List.of("en", "hu"));
        original.setNotificationPreferences(new Users.UserNotificationPreferencesRecord(false));
        when(access.canCurrentActorEditProfile("member")).thenReturn(true);
        when(users.findByUserId("member")).thenReturn(Optional.of(original));
        when(users.save(any(Users.class))).thenAnswer(call -> call.getArgument(0));
        UserDto request = profile("member", "Edited name", null, true, true);

        UserDto response = service.saveUserProfile(request);

        assertThat(response.name()).isEqualTo("Edited name");
        assertThat(response.admin()).isFalse(); assertThat(response.operator()).isFalse();
        assertThat(original.getAge()).isEqualTo(42);
        assertThat(original.getHeadline()).isEqualTo("Retained by a future extension");
        assertThat(original.getHostTier()).isEqualTo("Member");
        assertThat(original.getFirebaseUid()).isEqualTo("private-provider-uid");
        assertThat(original.getGoogleId()).isEqualTo("private-google-id");
        assertThat(original.getLanguages()).containsExactly("en", "hu");
        assertThat(original.getNotificationPreferences().muted()).isFalse();
        verify(users).save(original);
        verifyNoInteractions(jobs);
    }

    @Test void deniedProfileEditDoesNotReadOrWriteStorageOrResolveLocation() {
        assertThatThrownBy(() -> service.saveUserProfile(profile("other", "Changed", null, false, false)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(users, countries, telemetry, jobs);
    }

    @Test void profileJsonDoesNotExposeProviderIdentityOrExpandProductFields() {
        Users account = user("member"); account.setFirebaseUid("private-uid"); account.setEmail("private@example.test");
        var json = new tools.jackson.databind.ObjectMapper().valueToTree(service.toUserDto(account));
        assertThat(json.has("firebaseUid")).isFalse(); assertThat(json.has("email")).isFalse();
        assertThat(json.has("devices")).isFalse(); assertThat(json.has("affiliateRegistration")).isFalse();
        assertThat(json.has("age")).isFalse(); assertThat(json.has("impressions")).isFalse();
        assertThat(json.get("id").asText()).isEqualTo("member");
    }

    @Test void existingOperatorFlagIsPreservedWhenTheMinimalProfileIsSaved() {
        Users operator = user("operator"); operator.setOperator(true);
        when(access.canCurrentActorEditProfile("operator")).thenReturn(true);
        when(users.findByUserId("operator")).thenReturn(Optional.of(operator));
        when(users.save(any(Users.class))).thenAnswer(call -> call.getArgument(0));
        assertThat(service.saveUserProfile(profile("operator", "Name", null, false, false)).operator()).isTrue();
    }

    @Test void aChangedUnsupportedLocationIsRejectedBeforeSavingTheProfile() {
        Users original = user("member"); original.setPartitionKey("saved-country");
        original.setLocationCoordinates(new Users.LocationCoordinatesRecord(47.0, 19.0));
        region.setEnabled(true);
        when(access.canCurrentActorEditProfile("member")).thenReturn(true);
        when(users.findByUserId("member")).thenReturn(Optional.of(original));
        assertThatThrownBy(() -> service.saveUserProfile(profile("member", "Name",
                new LocationCoordinatesDto(0.0, 0.0), false, false)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
        verify(users, never()).save(any());
        assertThat(original.getLocationCoordinates()).isEqualTo(new Users.LocationCoordinatesRecord(47.0, 19.0));
    }

    @Test void disabledRegionGateDoesNotQueryTheCountryDatabaseForEligibility() {
        var response = service.queryLocationEligibility(null, null);
        assertThat(response.eligible()).isTrue(); assertThat(response.securityGateEnabled()).isFalse();
        verifyNoInteractions(countries);
    }

    @Test void demoSelectorChecksSharedCoordinatesOnceAndKeepsRoleFiltering() {
        region.setEnabled(true);
        Users first = user("first"); Users second = user("second"); Users admin = user("admin-demo-ava");
        for (Users account : List.of(first, second, admin))
            account.setLocationCoordinates(new Users.LocationCoordinatesRecord(47.0, 19.0));
        when(users.findAllByOrderByNameAscUserIdAsc()).thenReturn(List.of(first, second, admin));
        when(countries.resolvePartitionKey(new LocationCoordinatesDto(47.0, 19.0))).thenReturn("country");
        assertThat(service.queryDemoUserListItems(null, "member")).extracting(DemoUserListItemDto::id)
                .containsExactly("first", "second");
        verify(countries).resolvePartitionKey(new LocationCoordinatesDto(47.0, 19.0));
        verifyNoMoreInteractions(countries);
    }

    @Test void locationOnlyUpdateUsesTheNativeTargetedWrite() {
        Users account = user("member");
        when(selected.resolveSelectedUserId("member")).thenReturn("member");
        when(users.findByUserId("member")).thenReturn(Optional.of(account));
        when(access.canCurrentActorEditProfile("member")).thenReturn(true);
        when(countries.resolvePartitionKey(new LocationCoordinatesDto(47.0, 19.0), null)).thenReturn("country");
        var response = service.loadProfileExtById("member", 47.0, 19.0);
        assertThat(response.profileExt().profile().partitionKey()).isEqualTo("country");
        verify(users).updateLocation("member", new Users.LocationCoordinatesRecord(47.0, 19.0), "country");
        verify(users, never()).save(any());
        verify(countries).resolvePartitionKey(new LocationCoordinatesDto(47.0, 19.0), null);
        verifyNoMoreInteractions(countries);
    }

    @Test void malformedLocationSampleDoesNotTouchTheDatabase() {
        assertThatThrownBy(() -> service.loadProfileExtById("member", 91.0, 19.0))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(users, countries);
    }

    @Test void deletionPreservesItsNativePendingWorkAndCountsTheAccountOnlyOnce() {
        Users account = user("member");
        when(selected.resolveSelectedUserId("member")).thenReturn("member");
        when(access.canCurrentActorDeleteProfile("member")).thenReturn(true);
        when(users.findByUserId("member")).thenReturn(Optional.of(account));
        assertThat(service.deleteUser(new UserDeleteRequestDto("member")).submitted()).isTrue();
        assertThat(service.deleteUser(new UserDeleteRequestDto("member")).submitted()).isTrue();
        assertThat(account.getStatus()).isEqualTo("D"); assertThat(account.getProfileStatus()).isEqualTo("deleted");
        assertThat(account.getPreviousProfileStatus()).isEqualTo("public");
        assertThat(account.getDeletedAtIso()).isNotBlank();
        verify(statusWork, times(2)).save(account);
        verify(jobs).adjustPendingCount(NotificationJobs.RULE_ACCOUNT_PURGE, 1, "admin.jobs.table.affected");
    }

    private Users user(String id) {
        Users account = new Users(); account.setUserId(id); account.setName(id);
        account.setStatus("A"); account.setProfileStatus("public");
        return account;
    }

    private UserDto profile(String id, String name, LocationCoordinatesDto location, boolean admin, boolean operator) {
        return new UserDto(id, name, "City", "N", "man", location, "", List.of("/avatar.webp"),
                "public", "", "", admin, operator, null, new UserNotificationPreferencesDto(true));
    }
}
