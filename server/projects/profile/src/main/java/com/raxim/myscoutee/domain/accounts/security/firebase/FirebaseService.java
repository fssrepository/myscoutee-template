package com.raxim.myscoutee.domain.accounts.security.firebase;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebaseCredential;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.accounts.security.firebase.exception.FirebaseTokenInvalidException;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.platform.api.AdministratorSettings;

@Service
public class FirebaseService implements com.raxim.myscoutee.domain.accounts.api.BootstrapAccounts {
    private final RuntimeConfiguration config;
    private final UsersRepository usersRepository;
    private final AdministratorSettings appSettingsService;
    private static final String PROFILE_STATUS_ONBOARDING = "onboarding";

    public FirebaseService(
            RuntimeConfiguration config,
            UsersRepository usersRepository,
            AdministratorSettings appSettingsService) {
        this.config = config;
        this.usersRepository = usersRepository;
        this.appSettingsService = appSettingsService;
    }

    public UserDetails loadUserById(String profileId) {
        Users user = findUser(profileId);
        String normalizedProfileId = user != null ? normalizeId(user.getId()) : normalizeId(profileId);
        String memberEmail = normalizeEmail(config.getMemberUser());
        String storedEmail = user != null && StringUtils.hasText(user.getEmail())
                ? normalizeEmail(user.getEmail())
                : "";
        boolean explicitOperator = isStoredOperator(user)
                || (StringUtils.hasText(storedEmail) && config.isOperatorUser(storedEmail));
        String email = StringUtils.hasText(storedEmail)
                ? storedEmail
                : explicitOperator
                        ? memberEmail
                        : developmentMemberEmail(normalizedProfileId, memberEmail);
        return new FirebasePrincipal(normalizedProfileId, email, resolveAuthorities(email, user), user);
    }

    public UserDetails loadDemoOperatorById(String profileId) {
        Users user = findUser(profileId);
        if (user == null || !isStoredOperator(user)) {
            return null;
        }
        String email = normalizeEmail(StringUtils.hasText(user.getEmail())
                ? user.getEmail()
                : user.getId() + "@demo.local");
        return new FirebasePrincipal(
                normalizeId(user.getId()),
                email,
                resolveAuthorities(email, user),
                user,
                true);
    }

    public UserDetails loadOrCreateBootstrapOperator(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!config.isOperatorUser(normalizedEmail)) {
            return null;
        }
        Users user = findUserByEmail(normalizedEmail);
        if (user == null) {
            user = findUser(stableUserId(normalizedEmail));
        }
        if (user == null) {
            user = createOperatorUser(normalizedEmail, null);
        }
        user = ensureBootstrapOperator(user, normalizedEmail);
        return operatorPrincipal(normalizedEmail, user);
    }

    public UserDetails loadStoredBootstrapOperator(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!config.isOperatorUser(normalizedEmail)) {
            return null;
        }
        Users user = findUserByEmail(normalizedEmail);
        if (user == null) {
            user = findUser(stableUserId(normalizedEmail));
        }
        return isActiveStoredOperator(user)
                ? operatorPrincipal(normalizedEmail, user)
                : null;
    }

    public UserDetails loadUserByUsername(String username, String xLink, String currentPage) {
        String normalizedEmail = normalizeEmail(username);
        Users user = findUserByEmail(normalizedEmail);
        if (user == null) {
            user = findUser(username);
        }
        String resolvedUserId = user != null ? normalizeId(user.getId()) : stableUserId(normalizedEmail);
        return new FirebasePrincipal(
                resolvedUserId,
                normalizedEmail,
                resolveAuthorities(normalizedEmail, user),
                user);
    }

    public UserDetails loadUserByCredential(FirebaseCredential credential, String xLink, String currentPage) {
        String normalizedEmail = normalizeCredentialEmail(credential);
        boolean configuredAdmin = isAdminEmail(normalizedEmail);
        Users configuredAdminUser = configuredAdmin ? findUser(normalizedEmail) : null;
        Users emailUser = configuredAdminUser != null
                ? configuredAdminUser
                : findUserByEmail(normalizedEmail);
        Users providerUser = findUserByProvider(credential);
        if (emailUser != null
                && providerUser != null
                && !normalizeId(emailUser.getId()).equals(normalizeId(providerUser.getId()))) {
            throw new FirebaseTokenInvalidException(
                    "The verified email and provider identity belong to different users.");
        }
        Users user = providerUser != null ? providerUser : emailUser;
        if (user == null) {
            user = findUser(stableUserId(normalizedEmail));
        }
        if (user == null && config.isOperatorUser(normalizedEmail)) {
            user = createOperatorUser(normalizedEmail, credential);
        } else if (user == null && !configuredAdmin) {
            user = createOnboardingUser(normalizedEmail, credential);
        } else if (user != null) {
            user = syncFirebaseIdentity(user, credential);
        }
        String resolvedUserId = user != null ? normalizeId(user.getId()) : stableUserId(normalizedEmail);
        return new FirebasePrincipal(
                resolvedUserId,
                normalizedEmail,
                resolveAuthorities(normalizedEmail, user),
                user,
                credential.isEmailVerified());
    }

    private Users findUser(String userId) {
        String normalizedUserId = normalizeId(userId);
        if (!StringUtils.hasText(normalizedUserId)) {
            return null;
        }
        java.util.Optional<Users> userById = usersRepository.findByUserId(normalizedUserId);
        Users user = userById == null ? null : userById.orElse(null);
        if (user != null) {
            return user;
        }
        return usersRepository.findById(normalizedUserId).orElse(null);
    }

    private Users findUserByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return null;
        }
        java.util.Optional<Users> userByEmail = usersRepository.findFirstByEmail(normalizedEmail);
        return userByEmail == null ? null : userByEmail.orElse(null);
    }

    private Users findUserByProvider(FirebaseCredential credential) {
        String facebookId = normalizeProviderId(credential == null ? null : credential.getFacebookId());
        if (StringUtils.hasText(facebookId)) {
            java.util.Optional<Users> userByFacebookId = usersRepository.findFirstByFacebookId(facebookId);
            Users user = userByFacebookId == null ? null : userByFacebookId.orElse(null);
            if (user != null) {
                return user;
            }
        }
        String googleId = normalizeProviderId(credential == null ? null : credential.getGoogleId());
        if (StringUtils.hasText(googleId)) {
            java.util.Optional<Users> userByGoogleId = usersRepository.findFirstByGoogleId(googleId);
            Users user = userByGoogleId == null ? null : userByGoogleId.orElse(null);
            if (user != null) {
                return user;
            }
        }
        String firebaseUid = normalizeProviderId(credential == null ? null : credential.getUid());
        if (StringUtils.hasText(firebaseUid)) {
            java.util.Optional<Users> userByFirebaseUid = usersRepository.findFirstByFirebaseUid(firebaseUid);
            return userByFirebaseUid == null ? null : userByFirebaseUid.orElse(null);
        }
        return null;
    }

    private Users createOnboardingUser(String email, FirebaseCredential credential) {
        String normalizedEmail = normalizeEmail(email);
        String userId = stableUserId(normalizedEmail);
        Users existing = findUser(userId);
        if (existing != null) {
            return existing;
        }

        String displayName = credential == null ? null : credential.getName();
        String nowIso = Instant.now().toString();
        Users user = new Users();
        user.setMongoId(new ObjectId().toHexString());
        user.setSyncKey(UUID.randomUUID().toString());
        user.setUserId(userId);
        user.setEmail(normalizedEmail);
        applyFirebaseIdentity(user, credential);
        user.setName(resolveDisplayName(displayName));
        user.setInitials(resolveInitials(StringUtils.hasText(displayName) ? displayName : normalizedEmail));
        user.setGender("");
        user.setStatus("A");
        user.setProfileStatus(PROFILE_STATUS_ONBOARDING);
        user.setCompletion(0);
        user.setProfileFormVersion(0);
        user.setDeletedAtIso(nowIso);
        user.setActivities(new Users.UserActivitiesRecord(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new Users.UserChatCountersRecord(0, 0, 0, 0, 0, 0),
                new Users.UserEventCountersRecord(0, 0, 0, 0, 0, 0, 0),
                new Users.UserAssetCountersRecord(0, 0, 0, 0),
                new Users.UserEventFeedbackCountersRecord(0, 0, 0, 0),
                0, 0, 0));
        try {
            return usersRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            Users racedUser = findUser(userId);
            if (racedUser != null) {
                return syncFirebaseIdentity(racedUser, credential);
            }
            throw exception;
        }
    }

    private Users createOperatorUser(String email, FirebaseCredential credential) {
        String normalizedEmail = normalizeEmail(email);
        String userId = stableUserId(normalizedEmail);
        Users existing = findUser(userId);
        if (existing != null) {
            if (!existing.isOperator()) {
                existing.setOperator(true);
                existing.setHostTier("Operator");
                return usersRepository.save(existing);
            }
            return syncFirebaseIdentity(existing, credential);
        }

        String displayName = credential == null ? null : credential.getName();
        Users user = new Users();
        user.setMongoId(new ObjectId().toHexString());
        user.setSyncKey(UUID.randomUUID().toString());
        user.setUserId(userId);
        user.setEmail(normalizedEmail);
        applyFirebaseIdentity(user, credential);
        user.setName(StringUtils.hasText(displayName) ? displayName.trim() : "Operator");
        user.setInitials(resolveInitials(StringUtils.hasText(displayName) ? displayName : normalizedEmail));
        user.setGender("");
        user.setStatus("A");
        user.setProfileStatus("public");
        user.setStatusText("Operator workspace");
        user.setHostTier("Operator");
        user.setOperator(true);
        user.setCompletion(0);
        user.setProfileFormVersion(0);
        user.setActivities(new Users.UserActivitiesRecord(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new Users.UserChatCountersRecord(0, 0, 0, 0, 0, 0),
                new Users.UserEventCountersRecord(0, 0, 0, 0, 0, 0, 0),
                new Users.UserAssetCountersRecord(0, 0, 0, 0),
                new Users.UserEventFeedbackCountersRecord(0, 0, 0, 0),
                0, 0, 0));
        try {
            return usersRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            Users racedUser = findUser(userId);
            if (racedUser != null) {
                if (!racedUser.isOperator()) {
                    racedUser.setOperator(true);
                    racedUser.setHostTier("Operator");
                    return usersRepository.save(racedUser);
                }
                return syncFirebaseIdentity(racedUser, credential);
            }
            throw exception;
        }
    }

    private UserDetails operatorPrincipal(
            String normalizedEmail,
            Users user) {
        if (user == null || !isStoredOperator(user)) {
            return null;
        }
        return new FirebasePrincipal(
                normalizeId(user.getId()),
                normalizedEmail,
                resolveAuthorities(normalizedEmail, user),
                user);
    }

    private Users ensureBootstrapOperator(
            Users user,
            String normalizedEmail) {
        boolean changed = false;
        if (!user.isOperator()) {
            user.setOperator(true);
            changed = true;
        }
        if (!"Operator".equals(user.getHostTier())) {
            user.setHostTier("Operator");
            changed = true;
        }
        if (!"Operator workspace".equals(user.getStatusText())) {
            user.setStatusText("Operator workspace");
            changed = true;
        }
        if (!"A".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("A");
            changed = true;
        }
        if (!"public".equalsIgnoreCase(user.getProfileStatus())) {
            user.setProfileStatus("public");
            changed = true;
        }
        if (StringUtils.hasText(user.getDeletedAtIso())) {
            user.setDeletedAtIso(null);
            changed = true;
        }
        if (!StringUtils.hasText(user.getName())) {
            user.setName("Operator");
            changed = true;
        }
        if (!StringUtils.hasText(user.getInitials())) {
            user.setInitials(resolveInitials(normalizedEmail));
            changed = true;
        }
        return changed ? usersRepository.save(user) : user;
    }

    private Users syncFirebaseIdentity(Users user, FirebaseCredential credential) {
        if (user == null || credential == null) {
            return user;
        }
        String beforeFirebaseUid = normalizeProviderId(user.getFirebaseUid());
        String beforeGoogleId = normalizeProviderId(user.getGoogleId());
        String beforeFacebookId = normalizeProviderId(user.getFacebookId());
        String beforeEmail = normalizeEmail(user.getEmail());
        String credentialEmail = normalizeCredentialEmail(credential);
        user.setEmail(credentialEmail);
        applyFirebaseIdentity(user, credential);
        boolean changed = !beforeEmail.equals(credentialEmail)
                || !beforeFirebaseUid.equals(normalizeProviderId(user.getFirebaseUid()))
                || !beforeGoogleId.equals(normalizeProviderId(user.getGoogleId()))
                || !beforeFacebookId.equals(normalizeProviderId(user.getFacebookId()));
        return changed ? usersRepository.save(user) : user;
    }

    private void applyFirebaseIdentity(Users user, FirebaseCredential credential) {
        if (user == null || credential == null) {
            return;
        }
        assignIfPresent(user::setFirebaseUid, credential.getUid());
        assignIfPresent(user::setGoogleId, credential.getGoogleId());
        assignIfPresent(user::setFacebookId, credential.getFacebookId());
    }

    private void assignIfPresent(java.util.function.Consumer<String> setter, String value) {
        String normalizedValue = normalizeProviderId(value);
        if (StringUtils.hasText(normalizedValue)) {
            setter.accept(normalizedValue);
        }
    }

    private String normalizeId(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return stableUserId(config.getMemberUser());
        }
        return profileId.trim();
    }

    private String normalizeEmail(String username) {
        if (username == null || username.isBlank()) {
            if (config.getMemberUser() != null && !config.getMemberUser().isBlank()) {
                return config.getMemberUser().trim().toLowerCase(Locale.ROOT);
            }
            return "dev@myscoutee.local";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String developmentMemberEmail(String profileId, String fallbackEmail) {
        String normalizedProfileId = normalizeId(profileId);
        if (!StringUtils.hasText(normalizedProfileId)) {
            return fallbackEmail;
        }
        return DevelopmentIdentityEmail.forProfile(normalizedProfileId);
    }

    private String normalizeProviderId(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeCredentialEmail(FirebaseCredential credential) {
        String email = credential == null ? null : credential.getEmail();
        if (StringUtils.hasText(email)) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        String uid = credential == null ? null : credential.getUid();
        if (StringUtils.hasText(uid)) {
            return uid.trim().toLowerCase(Locale.ROOT) + "@firebase.local";
        }
        return UUID.randomUUID().toString() + "@firebase.local";
    }

    private String resolveDisplayName(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveInitials(String value) {
        String normalizedValue = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalizedValue)) {
            return "U";
        }
        String emailLocalPart = normalizedValue.contains("@")
                ? normalizedValue.substring(0, normalizedValue.indexOf('@'))
                : normalizedValue;
        String[] words = emailLocalPart
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .split("\\s+");
        if (words.length == 0 || !StringUtils.hasText(words[0])) {
            return "U";
        }
        if (words.length == 1) {
            return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase();
        }
        return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase();
    }

    private String stableUserId(String email) {
        return UUID.nameUUIDFromBytes(normalizeEmail(email).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private List<String> resolveAuthorities(String email, Users user) {
        String normalizedEmail = normalizeEmail(email);
        LinkedHashSet<String> authorities = new LinkedHashSet<>();
        if (isAdminEmail(normalizedEmail)) {
            authorities.add("A");
        } else {
            authorities.add("U");
        }
        if (config.isOperatorUser(normalizedEmail) || isStoredOperator(user)) {
            authorities.add("O");
        }
        return List.copyOf(authorities);
    }

    private boolean isAdminEmail(String normalizedEmail) {
        return appSettingsService.isAdminEmail(normalizedEmail);
    }

    private boolean isStoredOperator(Users user) {
        return user != null && user.isOperator();
    }

    private boolean isActiveStoredOperator(Users user) {
        return isStoredOperator(user)
                && "A".equalsIgnoreCase(user.getStatus())
                && !StringUtils.hasText(user.getDeletedAtIso());
    }
}
