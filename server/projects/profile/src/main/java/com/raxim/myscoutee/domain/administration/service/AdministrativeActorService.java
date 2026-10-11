package com.raxim.myscoutee.domain.administration.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.accounts.api.AuthenticatedActor;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;
import com.raxim.myscoutee.domain.administration.api.AdministrativeActors;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminUserDto;
import com.raxim.myscoutee.domain.platform.api.AdministratorSettings;
import com.raxim.myscoutee.domain.platform.api.AuthenticationMode;

/** Administrator account identity, independent of moderation data. */
@Service
public class AdministrativeActorService implements AdministrativeActors, com.raxim.myscoutee.domain.administration.api.AdministratorClassification {
    private final AuthenticationMode configProperties;
    private final ProfileSummaries usersRepository;
    private final AdministratorSettings appSettingsService;

    public AdministrativeActorService(AuthenticationMode configProperties, ProfileSummaries usersRepository,
            AdministratorSettings appSettingsService) {
        this.configProperties = configProperties;
        this.usersRepository = usersRepository;
        this.appSettingsService = appSettingsService;
    }

    @Override
    public AdminUserDto resolveAdminActor(String adminUserId) {
        return resolveAdmin(adminUserId);
    }

    private AdminUserDto resolveAdmin(String adminUserId) {
        if (configProperties.isFirebaseEnabled()) {
            AdminUserDto account = resolveFirebaseAdmin();
            return resolveSelectedAdmin(account, adminUserId);
        }
        if (MongoSessionModeContext.currentMode() != MongoSessionMode.DEMO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin demo access is restricted to demo data.");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(role -> "A".equals(role.getAuthority()))
                || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is restricted.");
        }
        String id = normalize(actor.getUserId());
        if (!StringUtils.hasText(id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        AdminUserDto admin = new AdminUserDto(id, id, initialsForEmail(id), normalize(actor.getEmail()),
                "Moderation workspace", "Reviews reports, feedback, and support chats.", List.of());
        return resolveSelectedAdmin(withStoredAdminProfile(admin), adminUserId);
    }

    private AdminUserDto resolveSelectedAdmin(AdminUserDto account, String requestedId) {
        if (!StringUtils.hasText(requestedId) || account.id().equals(requestedId)) return account;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private AdminUserDto resolveFirebaseAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        String email = principal instanceof AuthenticatedActor firebasePrincipal
                ? normalize(firebasePrincipal.getEmail())
                : normalize(authentication == null ? null : authentication.getName());
        if (!appSettingsService.isAdminEmail(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is restricted.");
        }
        return withStoredAdminProfile(new AdminUserDto(
                email,
                email,
                initialsForEmail(email),
                email,
                "Moderation workspace",
                "Reviews reports, feedback, and support chats.",
                List.of()));
    }

    private AdminUserDto withStoredAdminProfile(AdminUserDto admin) {
        ProfileSummary user = usersRepository.findByUserId(admin.id()).orElse(null);
        if (user == null) {
            return admin;
        }
        String name = text(user.getName(), admin.name());
        String initials = text(user.getInitials(), admin.initials());
        String headline = text(user.getHeadline(), admin.headline());
        String about = text(user.getAbout(), admin.about());
        List<String> images = user.getImages() == null
                ? admin.images()
                : user.getImages().stream()
                        .map(this::normalize)
                        .filter(StringUtils::hasText)
                        .toList();
        return new AdminUserDto(admin.id(), name, initials, admin.email(), headline, about, images);
    }

    private String normalize(String value) { return value == null ? "" : value.trim(); }
    private String text(String value, String fallback) { return StringUtils.hasText(normalize(value)) ? normalize(value) : fallback; }
    private String initialsForEmail(String email) {
        String[] parts = normalize(email).split("@")[0].split("[.\\-_\\s]+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) out.append(Character.toUpperCase(part.charAt(0)));
            if (out.length() >= 2) break;
        }
        return out.length() == 0 ? "AD" : out.toString();
    }
}
