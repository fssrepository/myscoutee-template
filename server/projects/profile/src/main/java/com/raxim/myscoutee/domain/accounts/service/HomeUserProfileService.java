package com.raxim.myscoutee.domain.accounts.service;

import java.time.Instant;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.raxim.myscoutee.domain.accounts.api.ProfileReadState;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.*;
import com.raxim.myscoutee.domain.accounts.security.firebase.DevelopmentIdentityEmail;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.access.config.RegionSecurityProperties;
import com.raxim.myscoutee.domain.platform.api.CountryDirectory;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.notifications.api.NotificationInbox;
import com.raxim.myscoutee.common.dto.SharedDtos.LocationCoordinatesDto;

@Service
@EnableConfigurationProperties(RegionSecurityProperties.class)
public class HomeUserProfileService implements ProfileReadState {
    private static final String PROFILE_STATUS_ONBOARDING = "onboarding";
    private static final String INELIGIBLE_REGION_MESSAGE = "Login is currently unavailable from your country or region for security reasons. Please come back later.";
    private final SelectedProfile selectedProfile;
    private final UsersRepository usersRepository;
    private final AccessPermissions accessControlService;
    private final CountryDirectory countryPartitionService;
    private final RegionSecurityProperties regionSecurityProperties;
    private final AccountLifecycleService lifecycle;
    private final OperationalTelemetry adminTelemetryService;
    private final ProfileStatusWork profileStatusWork;
    private final NotificationJobs notificationRuleRegistryService;
    private final NotificationInbox notifications;

    public HomeUserProfileService(SelectedProfile selectedProfile, UsersRepository usersRepository,
            AccessPermissions accessControlService, CountryDirectory countryPartitionService,
            RegionSecurityProperties regionSecurityProperties, AccountLifecycleService lifecycle,
            OperationalTelemetry adminTelemetryService, ProfileStatusWork profileStatusWork,
            NotificationJobs notificationRuleRegistryService, NotificationInbox notifications) {
        this.selectedProfile = selectedProfile; this.usersRepository = usersRepository;
        this.accessControlService = accessControlService; this.countryPartitionService = countryPartitionService;
        this.regionSecurityProperties = regionSecurityProperties; this.lifecycle = lifecycle;
        this.adminTelemetryService = adminTelemetryService; this.profileStatusWork = profileStatusWork;
        this.notificationRuleRegistryService = notificationRuleRegistryService; this.notifications = notifications;
    }

    public UserByIdResponseDto queryUserById(String requestedUserId) {
        String userId = resolveSelectedUserId(requestedUserId);
        if (!StringUtils.hasText(userId))
            return StringUtils.hasText(requestedUserId) ? null : buildCurrentPrivilegedPrincipalResponse();
        Users user = findUser(userId);
        if (user == null) return buildCurrentPrivilegedPrincipalResponse();
        return response(toUserDto(user), isCurrentRequestAdmin(), isCurrentRequestOperator());
    }

    private UserByIdResponseDto response(UserDto profile, boolean admin, boolean operator) {
        return new UserByIdResponseDto(profile.id(), profile.name(), profile.city(), profile.initials(), profile.gender(),
                profile.locationCoordinates(), profile.partitionKey(), profile.images(), profile.profileStatus(),
                profile.previousProfileStatus(), profile.deletedAtIso(), admin, operator, profile.activities(),
                profile.notificationPreferences(), profile.activities());
    }

    public UserMenuCountersDto queryMenuCounters(String requestedUserId) {
        return buildMenuCounters(findUser(resolveSelectedUserId(requestedUserId)));
    }

    private UserMenuCountersDto buildMenuCounters(Users user) {
        var activities = user == null ? null : user.getActivities();
        var chat = activities == null ? null : activities.chat();
        var cases = chat == null ? null : chat.supportCases();
        var support = new UserSupportCaseCountersDto(
                cases == null ? 0 : count(cases.pending()), cases == null ? 0 : count(cases.warned()),
                cases == null ? 0 : count(cases.picked()), cases == null ? 0 : count(cases.solved()),
                cases == null ? 0 : count(cases.blocked()), cases == null ? 0 : count(cases.all()));
        return new UserMenuCountersDto(activities == null ? 0 : count(activities.chats()),
                new UserChatCountersDto(chat == null ? 0 : count(chat.all()), chat == null ? 0 : count(chat.appSupport()),
                        support, chat == null ? 0 : count(chat.contacts())),
                activities == null ? 0 : count(activities.notifications()));
    }
    public UserDto toUserDto(Users user) {
        if (user == null) return null;
        return new UserDto(normalizeId(user.getId()), safeText(user.getName()), safeText(user.getCity()),
                safeText(user.getInitials()), safeText(user.getGender()), toLocationCoordinatesDto(user.getLocationCoordinates()),
                safeText(user.getPartitionKey()), normalizeStrings(user.getImages()), resolveProfileStatus(user),
                safeText(user.getPreviousProfileStatus()), safeText(user.getDeletedAtIso()), false, user.isOperator(),
                buildMenuCounters(user), new UserNotificationPreferencesDto(user.getNotificationPreferences() != null
                        && Boolean.TRUE.equals(user.getNotificationPreferences().muted())));
    }

    public ProfileExtByIdResponseDto loadProfileExtById(String requestedUserId) {
        UserByIdResponseDto response = queryUserById(requestedUserId);
        if (response == null) return null;
        UserDto profile = new UserDto(response.id(), response.name(), response.city(), response.initials(), response.gender(),
                response.locationCoordinates(), response.partitionKey(), response.images(), response.profileStatus(),
                response.previousProfileStatus(), response.deletedAtIso(), response.admin(), response.operator(),
                response.activities(), response.notificationPreferences());
        return new ProfileExtByIdResponseDto(new ProfileExtDto(profile), response.counterOverrides());
    }

    public UserDto saveUserProfileExt(ProfileExtDto request) {
        return request == null ? null : saveUserProfile(request.profile());
    }

    public UserRealtimeLongPollResponseDto queryRealtimeSnapshot(UserRealtimeLongPollRequestDto request) {
        Users user = findUser(resolveSelectedUserId(request == null ? null : request.userId()));
        if (user == null) return null;
        UserMenuCountersDto stored = buildMenuCounters(user);
        UserMenuCountersDto counters = new UserMenuCountersDto(stored.chats(), stored.chat(),
                notifications.currentUnreadCount(user.getUserId()));
        String cursor = user.getUserId().replace(":", "%3A") + ":" + counters.chats() + ":" + counters.notifications()
                + ":" + counters.chat().supportCases() + ":" + safeText(user.getProfileStatus());
        return new UserRealtimeLongPollResponseDto(user.getUserId(), safeText(user.getProfileStatus()),
                toLocationCoordinatesDto(user.getLocationCoordinates()), counters,
                (user.getDevices() == null ? List.<Users.UserDeviceRecord>of() : user.getDevices()).stream()
                        .filter(Objects::nonNull).map(device -> new UserNotificationDeviceDto(device.getDeviceId(),
                                Boolean.TRUE.equals(device.getNotificationsEnabled()))).toList(), cursor, Instant.now().toString());
    }

    public ProfileExtByIdResponseDto loadProfileExtById(String requestedUserId, Double latitude, Double longitude) {
        if (latitude != null || longitude != null) {
            if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid location coordinates.");
            }
            Users selected = resolveCurrentUser(requestedUserId);
            if (selected == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found.");
            if (!accessControlService.canCurrentActorEditProfile(selected.getUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user is not allowed to edit this profile.");
            }
            var coordinates = new LocationCoordinatesDto(latitude, longitude);
            Users account = StringUtils.hasText(selected.getAccountUserId())
                    ? usersRepository.findByUserId(selected.getAccountUserId()).orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found.")) : selected;
            String resolvedPartition = countryPartitionService.resolvePartitionKey(coordinates, account.getPartitionKey());
            if (regionSecurityProperties.isEnabled() && !StringUtils.hasText(resolvedPartition)) {
                // Discard an unsupported pending sample, not the already admitted profile load.
                if (account.getLocationCoordinates() != null) return loadProfileExtById(requestedUserId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, INELIGIBLE_REGION_MESSAGE);
            }
            // Resolve the point once for the account and group profile, before either field update.
            String accountPartition = resolveStoredPartitionKey(resolvedPartition, account.getPartitionKey(), account);
            String selectedPartition = account == selected ? accountPartition
                    : resolveStoredPartitionKey(resolvedPartition, selected.getPartitionKey(), selected);
            persistLoadedLocation(account, coordinates, accountPartition);
            if (account != selected) persistLoadedLocation(selected, coordinates, selectedPartition);
        }
        return loadProfileExtById(requestedUserId);
    }

    private void persistLoadedLocation(Users user, LocationCoordinatesDto coordinates, String partition) {
        var record = toLocationCoordinatesRecord(coordinates);
        usersRepository.updateLocation(user.getUserId(), record, partition);
        user.setLocationCoordinates(record);
        user.setPartitionKey(partition);
    }

    private String validatedLocationPartition(Users current, LocationCoordinatesDto coordinates) {
        String resolved = countryPartitionService.resolvePartitionKey(coordinates, current == null ? null : current.getPartitionKey());
        String stored = current == null ? null : current.getPartitionKey();
        boolean retainsStored = StringUtils.hasText(stored)
                && sameLocation(coordinates, current.getLocationCoordinates());
        if (regionSecurityProperties.isEnabled() && coordinates != null
                && !StringUtils.hasText(resolved) && !retainsStored) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, INELIGIBLE_REGION_MESSAGE);
        }
        return resolveStoredPartitionKey(resolved, stored, current);
    }

    public UserLocationEligibilityResponseDto queryLocationEligibility(Double latitude, Double longitude) {
        if (!regionSecurityProperties.isEnabled()) {
            return new UserLocationEligibilityResponseDto(true, null, null, false, false);
        }

        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return new UserLocationEligibilityResponseDto(false, null, null, true, true);
        }

        LocationCoordinatesDto coordinates = new LocationCoordinatesDto(latitude, longitude);
        String partitionKey = countryPartitionService.resolvePartitionKey(coordinates);
        return new UserLocationEligibilityResponseDto(
                StringUtils.hasText(partitionKey),
                partitionKey,
                StringUtils.hasText(partitionKey) ? null : INELIGIBLE_REGION_MESSAGE,
                true,
                false);
    }

    public UserDto saveUserProfile(UserDto user) {
        if (user == null || !StringUtils.hasText(user.id())) return null;
        String userId = normalizeId(user.id());
        if (!accessControlService.canCurrentActorEditProfile(userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user is not allowed to edit this profile.");
        Users current = usersRepository.findByUserId(userId).orElse(null);
        boolean wasTracked = current != null && Boolean.TRUE.equals(current.getAccountPurgePendingCounterTracked());
        Users merged = mergeUserDocument(current, user);
        requireUniqueEmailOwner(merged);
        boolean shouldTrack = isAccountPurgeCandidate(merged);
        merged.setAccountPurgePendingCounterTracked(shouldTrack);
        Users saved;
        try { saved = usersRepository.save(merged); }
        catch (DataIntegrityViolationException conflict) { throwEmailConflictIfPresent(merged, conflict); throw conflict; }
        if (wasTracked != shouldTrack) adjustAccountPurgePendingCount(saved.getUserId(), shouldTrack ? 1 : -1);
        adminTelemetryService.record(current == null ? "profile.registered" : "profile.updated", "profiles", userId, userId);
        return toUserDto(saved);
    }

    private Users mergeUserDocument(Users current, UserDto user) {
        LocationCoordinatesDto requestedLocation = user.locationCoordinates();
        String locationPartition = validatedLocationPartition(current, requestedLocation);

        Users document = current == null ? new Users() : current;
        document.setMongoId(current != null && StringUtils.hasText(current.getMongoId()) ? current.getMongoId() : new ObjectId().toHexString());
        document.setSyncKey(current != null && StringUtils.hasText(current.getSyncKey()) ? current.getSyncKey() : UUID.randomUUID().toString());
        document.setUserId(normalizeId(user.id()));
        document.setEmail(current != null && StringUtils.hasText(current.getWorkspaceGroupId())
                ? null : resolveStoredEmail(normalizeId(user.id()), current == null ? null : current.getEmail()));
        document.setName(nullToEmpty(user.name()));
        document.setCity(nullToEmpty(user.city()));
        document.setInitials(nullToEmpty(user.initials()));
        document.setGender(nullToEmpty(user.gender()));
        document.setOperator((current != null && current.isOperator())
                || isCurrentRequestOperatorForUser(user.id()));
        document.setLocationCoordinates(toLocationCoordinatesRecord(requestedLocation));
        document.setPartitionKey(locationPartition);
        document.setImages(normalizeStrings(user.images()));
        boolean reactivatingDeletedUser = current != null
                && "D".equalsIgnoreCase(safeText(current.getStatus()))
                && !"deleted".equals(safeText(user.profileStatus()));
        String previousProfileStatus = current == null ? "" : safeText(current.getPreviousProfileStatus());
        if ("deleted".equals(safeText(user.profileStatus()))) {
            previousProfileStatus = StringUtils.hasText(user.previousProfileStatus())
                    ? safeText(user.previousProfileStatus())
                    : resolveProfileStatus(current);
        } else if (reactivatingDeletedUser) {
            previousProfileStatus = "";
        }
        String status = current != null && "D".equalsIgnoreCase(safeText(current.getStatus())) && !reactivatingDeletedUser
                ? current.getStatus()
                : resolveStatusCode(user.profileStatus(), current == null ? null : current.getStatus());
        document.setStatus(status);
        String resolvedProfileStatus = resolveProfileStatus(status, user.profileStatus());
        document.setProfileStatus(resolvedProfileStatus);
        document.setPreviousProfileStatus(previousProfileStatus);
        if ("D".equalsIgnoreCase(safeText(status))) {
            document.setDeletedAtIso(StringUtils.hasText(user.deletedAtIso())
                    ? user.deletedAtIso()
                    : (current == null ? Instant.now().toString() : current.getDeletedAtIso()));
        } else if (PROFILE_STATUS_ONBOARDING.equals(resolvedProfileStatus)) {
            document.setDeletedAtIso(StringUtils.hasText(user.deletedAtIso())
                    ? user.deletedAtIso()
                    : (current != null && StringUtils.hasText(current.getDeletedAtIso())
                            ? current.getDeletedAtIso()
                            : Instant.now().toString()));
        } else {
            document.setDeletedAtIso(null);
        }
        return document;
    }

    private boolean isAccountPurgeCandidate(Users user) {
        String profileStatus = safeText(user == null ? null : user.getProfileStatus());
        return user != null
                && StringUtils.hasText(safeText(user.getDeletedAtIso()))
                && ("deleted".equals(profileStatus) || PROFILE_STATUS_ONBOARDING.equals(profileStatus));
    }

    private void adjustAccountPurgePendingCount(String userId, int delta) {
        lifecycle.adjustAccountPurgePendingCount(userId, delta);
    }

    public UserSubmitActionResponseDto logout(UserLogoutRequestDto request) {
        return lifecycle.logout(request);
    }

    public UserSubmitActionResponseDto deleteUser(UserDeleteRequestDto request) {
        return lifecycle.deleteUser(request);
    }

    public UserSubmitActionResponseDto scheduleUserDeletionByUserId(String userId, String source) {
        return lifecycle.scheduleUserDeletionByUserId(userId, source);
    }

    private String resolveProfileStatus(Users user) {
        return resolveProfileStatus(user == null ? null : user.getStatus(), user == null ? null : user.getProfileStatus());
    }

    private String resolveProfileStatus(String status, String fallbackProfileStatus) {
        String normalizedProfileStatus = safeText(fallbackProfileStatus);
        if ("blocked".equals(normalizedProfileStatus)) {
            return "blocked";
        }
        if (PROFILE_STATUS_ONBOARDING.equals(normalizedProfileStatus)) {
            return PROFILE_STATUS_ONBOARDING;
        }
        return switch (safeText(status)) {
            case "D" -> "deleted";
            case "B" -> "blocked";
            case "I" -> "inactive";
            case "F" -> "friends only";
            case "H" -> "host only";
            case "A" -> "public";
            default -> {
                yield StringUtils.hasText(normalizedProfileStatus) ? normalizedProfileStatus : "public";
            }
        };
    }

    private String resolveStatusCode(String profileStatus, String fallbackStatus) {
        return switch (safeText(profileStatus)) {
            case "deleted" -> "D";
            case "blocked" -> "B";
            case PROFILE_STATUS_ONBOARDING -> "A";
            case "inactive" -> "I";
            case "friends only" -> "F";
            case "host only" -> "H";
            case "public" -> "A";
            default -> StringUtils.hasText(safeText(fallbackStatus)) ? safeText(fallbackStatus) : "A";
        };
    }

    private void requireUniqueEmailOwner(Users user) {
        if (user == null) {
            return;
        }
        String normalizedEmail = normalizeUserEmail(user.getEmail());
        user.setEmail(StringUtils.hasText(normalizedEmail) ? normalizedEmail : null);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        Users currentOwner = usersRepository.findFirstByEmail(normalizedEmail).orElse(null);
        if (currentOwner != null
                && !normalizeId(currentOwner.getId()).equals(normalizeId(user.getId()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email address is already registered.");
        }
    }

    private void throwEmailConflictIfPresent(
            Users attemptedUser,
            DataIntegrityViolationException exception) {
        String normalizedEmail = normalizeUserEmail(
                attemptedUser == null ? null : attemptedUser.getEmail());
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        Users currentOwner = usersRepository.findFirstByEmail(normalizedEmail).orElse(null);
        if (currentOwner != null
                && !normalizeId(currentOwner.getId()).equals(
                        normalizeId(attemptedUser.getId()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email address is already registered.",
                    exception);
        }
    }

    private String normalizeUserEmail(String email) {
        return safeText(email).toLowerCase(Locale.ROOT);
    }

    private String resolveStoredPartitionKey(
            String resolvedPartitionKey,
            String currentPartitionKey,
            Users partitionOwner) {
        if (StringUtils.hasText(resolvedPartitionKey)) {
            return resolvedPartitionKey;
        }

        String normalizedCurrentPartitionKey = safeText(currentPartitionKey);
        if (StringUtils.hasText(normalizedCurrentPartitionKey)) {
            return normalizedCurrentPartitionKey;
        }

        return null;
    }

    private boolean sameLocation(
            LocationCoordinatesDto requested,
            Users.LocationCoordinatesRecord stored) {
        return requested != null
                && stored != null
                && Objects.equals(requested.latitude(), stored.latitude())
                && Objects.equals(requested.longitude(), stored.longitude());
    }

    private String resolveStoredEmail(String userId, String fallbackEmail) {
        String resolvedPrincipalEmail = resolveAuthenticatedEmailForUser(userId);
        if (StringUtils.hasText(resolvedPrincipalEmail)) {
            return normalizeUserEmail(resolvedPrincipalEmail);
        }
        if (!StringUtils.hasText(fallbackEmail) && userId.startsWith("demo-profile-")) {
            return DevelopmentIdentityEmail.forProfile(userId);
        }
        return normalizeUserEmail(fallbackEmail);
    }

    private String resolveAuthenticatedEmailForUser(String userId) {
        String normalizedUserId = normalizeId(userId);
        if (!StringUtils.hasText(normalizedUserId)) {
            return "";
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FirebasePrincipal firebasePrincipal)) {
            return "";
        }
        String principalUserId = firebasePrincipal.getUser() != null
                ? normalizeId(firebasePrincipal.getUser().getId())
                : normalizeId(firebasePrincipal.getUserId());
        if (!normalizedUserId.equals(principalUserId)) {
            return "";
        }
        return safeText(firebasePrincipal.getEmail());
    }

    private LocationCoordinatesDto toLocationCoordinatesDto(Users.LocationCoordinatesRecord record) {
        if (record == null) {
            return null;
        }
        return new LocationCoordinatesDto(record.latitude(), record.longitude());
    }

    private Users.LocationCoordinatesRecord toLocationCoordinatesRecord(LocationCoordinatesDto dto) {
        if (dto == null) {
            return null;
        }
        return new Users.LocationCoordinatesRecord(dto.latitude(), dto.longitude());
    }

    private boolean isCurrentRequestAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "A".equals(authority.getAuthority()));
    }

    private boolean isCurrentRequestOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "O".equals(authority.getAuthority()));
    }

    private boolean isCurrentRequestOperatorForUser(String userId) {
        String normalizedUserId = normalizeId(userId);
        if (!StringUtils.hasText(normalizedUserId) || !isCurrentRequestOperator()) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof FirebasePrincipal firebasePrincipal)) {
            return false;
        }
        return normalizedUserId.equals(normalizeId(firebasePrincipal.getUserId()));
    }

    private boolean demoLocationRequired(Users user, Map<LocationCoordinatesDto, Boolean> checks) {
        LocationCoordinatesDto point = toLocationCoordinatesDto(user.getLocationCoordinates());
        if (point == null || point.latitude() == null || point.longitude() == null
                || !Double.isFinite(point.latitude()) || !Double.isFinite(point.longitude())
                || Math.abs(point.latitude()) > 90 || Math.abs(point.longitude()) > 180) return true;
        // The existing cached selector checks shared seed coordinates once, not once per profile.
        return checks.computeIfAbsent(point, coordinates ->
                !queryLocationEligibility(coordinates.latitude(), coordinates.longitude()).eligible());
    }

    private String resolveDemoUserAvatarUrl(Users user) {
        return normalizeStrings(user.getImages()).stream()
                .findFirst()
                .map(imageUrl -> imageUrl.endsWith("/large.webp")
                        ? imageUrl.substring(0, imageUrl.length() - "/large.webp".length()) + "/small.webp"
                        : imageUrl)
                .orElse("");
    }

    private boolean isSelectableDemoUserDocument(Users user, String selectorRole) {
        if (!isUsableUserDocument(user)
                || PROFILE_STATUS_ONBOARDING.equals(resolveProfileStatus(user))) {
            return false;
        }
        return switch (selectorRole) {
            case "admin" -> isDemoAdminUserDocument(user);
            case "operator" -> isDemoOperatorUserDocument(user);
            default -> !isDemoAdminUserDocument(user) && !isDemoOperatorUserDocument(user);
        };
    }

    private boolean isDemoAdminUserDocument(Users user) {
        return user != null
                && (normalizeId(user.getId()).startsWith("admin-demo-")
                        || "admin".equalsIgnoreCase(safeText(user.getHostTier())));
    }

    private boolean isDemoOperatorUserDocument(Users user) {
        return user != null && user.isOperator();
    }

    private String normalizeDemoSelectorRole(String role) {
        String normalized = safeText(role).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "admin", "operator" -> normalized;
            default -> "member";
        };
    }

    private String adminDisplayName(String email) {
        String normalizedEmail = safeText(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return "Admin";
        }
        int atIndex = normalizedEmail.indexOf('@');
        String localPart = atIndex > 0 ? normalizedEmail.substring(0, atIndex) : normalizedEmail;
        String normalizedName = localPart.replaceAll("[^A-Za-z0-9]+", " ").trim();
        return StringUtils.hasText(normalizedName) ? normalizedName : "Admin";
    }

    private String adminInitials(String name) {
        String[] words = safeText(name).split("\\s+");
        List<String> usableWords = java.util.Arrays.stream(words)
                .filter(StringUtils::hasText)
                .toList();
        if (usableWords.isEmpty()) {
            return "A";
        }
        if (usableWords.size() == 1) {
            String word = usableWords.get(0);
            return word.substring(0, Math.min(2, word.length())).toUpperCase();
        }
        return (usableWords.get(0).substring(0, 1) + usableWords.get(1).substring(0, 1)).toUpperCase();
    }

    private List<String> normalizeStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private int normalizeCount(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private long resolveDemoUserListLimit(Integer value) {
        if (value == null) {
            return Long.MAX_VALUE;
        }
        return Math.min(500L, Math.max(1L, value.longValue()));
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptionalText(String value) {
        String normalized = normalizeId(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String safeText(String value) { return value == null ? "" : value.trim(); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private int count(Integer value) { return value == null ? 0 : Math.max(0, value); }
    private boolean isUsableUserDocument(Users user) { return SelectedProfileService.isUsableUserDocument(user); }
    private String resolveSelectedUserId(String requestedUserId) { return selectedProfile.resolveSelectedUserId(requestedUserId); }
    public Users resolveCurrentUser(String requestedUserId) { return findUser(resolveSelectedUserId(requestedUserId)); }
    private Users findUser(String userId) {
        if (!StringUtils.hasText(userId)) return null;
        Users user = usersRepository.findByUserId(userId).orElse(null);
        return isUsableUserDocument(user) ? user : null;
    }

    public List<DemoUserListItemDto> queryDemoUserListItems(Integer limit, String role) {
        String selectorRole = normalizeDemoSelectorRole(role);
        Map<LocationCoordinatesDto, Boolean> locationChecks = new HashMap<>();
        return usersRepository.findAllByOrderByNameAscUserIdAsc().stream()
                .filter(user -> user.getWorkspaceGroupId() == null)
                .filter(user -> isSelectableDemoUserDocument(user, selectorRole))
                .limit(resolveDemoUserListLimit(limit))
                .map(user -> new DemoUserListItemDto(normalizeId(user.getId()), toLocationCoordinatesDto(user.getLocationCoordinates()),
                        "member".equals(selectorRole) && demoLocationRequired(user, locationChecks), safeText(user.getName()),
                        safeText(user.getCity()), safeText(user.getInitials()), resolveDemoUserAvatarUrl(user),
                        safeText(user.getGender()), safeText(user.getStatusText()), count(user.getCompletion()),
                        count(user.getProfileFormVersion()), safeText(user.getStatus()), safeText(user.getProfileStatus()),
                        safeText(user.getDeletedAtIso()))).toList();
    }

    private UserByIdResponseDto buildCurrentPrivilegedPrincipalResponse() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = isCurrentRequestAdmin(); boolean operator = isCurrentRequestOperator();
        if (authentication == null || !(authentication.getPrincipal() instanceof FirebasePrincipal principal)
                || (!admin && !operator) || !StringUtils.hasText(principal.getUserId())) return null;
        String name = admin ? adminDisplayName(principal.getEmail()) : "Operator";
        return response(new UserDto(principal.getUserId(), name, "", adminInitials(name), "man", null, "", List.of(),
                "public", "", "", admin, operator, buildMenuCounters(null), new UserNotificationPreferencesDto(false)), admin, operator);
    }
}
