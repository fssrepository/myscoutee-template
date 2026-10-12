package com.raxim.myscoutee.domain.accounts.service;

import static com.raxim.myscoutee.domain.accounts.service.ProfileStatusValues.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.document.UserSessionAction;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.*;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.accounts.repository.UserSessionActionsRepository;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.platform.api.JobAudience;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

/** Existing logout audit and account-deletion initiation, without profile discovery. */
@Service
public class AccountLifecycleService {
    private final UsersRepository usersRepository;
    private final UserSessionActionsRepository userSessionActionsRepository;
    private final SelectedProfile selectedProfile;
    private final ProfileStatusWork profileStatusWork;
    private final AccessPermissions accessControlService;
    private final OperationalTelemetry adminTelemetryService;
    private final JobAudience jobGroupScope;
    private final NotificationJobs notificationRuleRegistryService;

    public AccountLifecycleService(UsersRepository usersRepository, UserSessionActionsRepository userSessionActionsRepository,
            SelectedProfile selectedProfile, ProfileStatusWork profileStatusWork, AccessPermissions accessControlService,
            OperationalTelemetry adminTelemetryService, JobAudience jobGroupScope,
            Optional<NotificationJobs> notificationRuleRegistryService) {
        this.usersRepository = usersRepository; this.userSessionActionsRepository = userSessionActionsRepository;
        this.selectedProfile = selectedProfile; this.profileStatusWork = profileStatusWork;
        this.accessControlService = accessControlService; this.adminTelemetryService = adminTelemetryService;
        this.jobGroupScope = jobGroupScope;
        this.notificationRuleRegistryService = notificationRuleRegistryService.orElse(null);
    }

    public UserSubmitActionResponseDto logout(UserLogoutRequestDto request) {
        String resolvedUserId = selectedProfile.resolveSelectedUserId(request == null ? null : request.userId());
        if (!StringUtils.hasText(resolvedUserId)) {
            return new UserSubmitActionResponseDto(false, "Unable to logout.");
        }
        recordSessionAction(resolvedUserId, "logout", "session");
        return new UserSubmitActionResponseDto(true, null);
    }

    public UserSubmitActionResponseDto deleteUser(UserDeleteRequestDto request) {
        String resolvedUserId = selectedProfile.resolveSelectedUserId(request == null ? null : request.userId());
        if (!StringUtils.hasText(resolvedUserId)) {
            return new UserSubmitActionResponseDto(false, "Unable to delete user.");
        }
        if (!accessControlService.canCurrentActorDeleteProfile(resolvedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user is not allowed to delete this profile.");
        }
        return scheduleUserDeletionByUserId(resolvedUserId, "status:D");
    }

    public UserSubmitActionResponseDto scheduleUserDeletionByUserId(String userId, String source) {
        String resolvedUserId = normalizeId(userId);
        if (!StringUtils.hasText(resolvedUserId)) {
            return new UserSubmitActionResponseDto(false, "Unable to delete user.");
        }

        Users user = usersRepository.findByUserId(resolvedUserId)
                .or(() -> usersRepository.findById(resolvedUserId))
                .orElse(null);
        if (user == null) {
            return new UserSubmitActionResponseDto(false, "Unable to delete user.");
        }

        String previousProfileStatus = resolveProfileStatus(user);
        boolean newlyTrackedForPurge = !Boolean.TRUE.equals(user.getAccountPurgePendingCounterTracked());
        user.setStatus("D");
        user.setProfileStatus("deleted");
        user.setPreviousProfileStatus("deleted".equals(previousProfileStatus) ? user.getPreviousProfileStatus() : previousProfileStatus);
        user.setDeletedAtIso(Instant.now().toString());
        user.setAccountPurgePendingCounterTracked(true);
        profileStatusWork.save(user);
        if (newlyTrackedForPurge) {
            adjustAccountPurgePendingCount(user.getUserId(), 1);
        }
        recordSessionAction(resolvedUserId, "delete", StringUtils.hasText(source) ? source : "status:D");
        adminTelemetryService.record("profile.deleted", "profiles", resolvedUserId, resolvedUserId);
        return new UserSubmitActionResponseDto(true, null);
    }

    void adjustAccountPurgePendingCount(String userId, int delta) {
        if (delta == 0 || notificationRuleRegistryService == null) {
            return;
        }
        JobGroupContext.run(jobGroupScope.forUser(userId), () -> notificationRuleRegistryService.adjustPendingCount(
                NotificationJobs.RULE_ACCOUNT_PURGE, delta, "admin.jobs.table.affected"));
    }

    private void recordSessionAction(String userId, String action, String metadata) {
        String normalizedUserId = normalizeId(userId);
        if (!StringUtils.hasText(normalizedUserId)) {
            return;
        }
        String nowIso = Instant.now().toString();
        UserSessionAction document = new UserSessionAction();
        document.setId(new ObjectId().toHexString());
        document.setSyncKey(UUID.randomUUID().toString());
        document.setUserId(normalizedUserId);
        document.setAction(safeText(action));
        document.setMetadata(safeText(metadata));
        document.setCreatedDate(nowIso);
        document.setCreatedUser(normalizedUserId);
        document.setUpdatedDate(nowIso);
        document.setUpdatedUser(normalizedUserId);
        userSessionActionsRepository.save(document);
    }

    private String normalizeId(String value) { return value == null ? "" : value.trim(); }
    private String safeText(String value) { return normalizeId(value); }
}
