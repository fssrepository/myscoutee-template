package com.raxim.myscoutee.domain.accounts.service;

import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Account selection shared by native requests and verified service delegation. */
@Service
public class SelectedProfileService implements SelectedProfile, com.raxim.myscoutee.domain.accounts.api.CurrentProfile {
    private final UsersRepository usersRepository;

    public SelectedProfileService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    public Users resolveCurrentUser(String requestedUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof FirebasePrincipal firebasePrincipal) {
            Users principalUser = firebasePrincipal.getUser();
            String principalUserId = principalUser != null
                    ? normalizeId(principalUser.getId())
                    : normalizeId(firebasePrincipal.getUserId());
            if (StringUtils.hasText(principalUserId)) {
                Users refreshedPrincipalUser = findUser(principalUserId);
                if (refreshedPrincipalUser != null) {
                    return refreshedPrincipalUser;
                }
            }
            if (isUsableUserDocument(principalUser)) {
                return principalUser;
            }
            // An authenticated identity must never fall back to a different
            // account selected through request data when its profile is missing.
            return null;
        }

        if (authentication != null
                && authentication.getPrincipal() instanceof com.raxim.myscoutee.domain.accounts.api.AuthenticatedActor actor) {
            if (!authentication.isAuthenticated()) return null;
            var identity = actor.getProfileIdentity();
            if (identity == null || !java.util.Objects.equals(actor.getUserId(), identity.userId())) return null;
            Users user = findUser(normalizeId(actor.getUserId()));
            // The signed selection is a constraint, not permission to switch to a
            // requested profile or to keep a profile after its ownership changes.
            return user != null
                    && java.util.Objects.equals(identity.accountUserId(), user.getAccountUserId())
                    && java.util.Objects.equals(identity.workspaceGroupId(), user.getWorkspaceGroupId()) ? user : null;
        }

        String normalizedRequestedUserId = normalizeId(requestedUserId);
        if (StringUtils.hasText(normalizedRequestedUserId)) {
            return findUser(normalizedRequestedUserId);
        }

        return null;
    }

    public String resolveSelectedUserId(String requestedUserId) {
        Users currentUser = resolveCurrentUser(requestedUserId);
        return currentUser == null ? null : normalizeId(currentUser.getId());
    }

    @Override public com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto resolveSelectedIdentity(String requestedUserId) {
        Users currentUser = resolveCurrentUser(requestedUserId);
        return currentUser == null ? null : new com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto(
                normalizeId(currentUser.getId()), currentUser.getAccountUserId(), currentUser.getWorkspaceGroupId());
    }

    private Users findUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        Users user = usersRepository.findByUserId(userId).orElse(null);
        return isUsableUserDocument(user) ? user : null;
    }

    static boolean isUsableUserDocument(Users user) {
        if (user == null || !StringUtils.hasText(normalizeId(user.getId()))) {
            return false;
        }
        return "onboarding".equals(normalizeId(user.getProfileStatus()))
                || StringUtils.hasText(user.getName())
                || user.getAge() != null
                || user.getLocationCoordinates() != null
                || (user.getImages() != null && user.getImages().stream()
                        .anyMatch(image -> image != null && StringUtils.hasText(image.trim())));
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }
}
