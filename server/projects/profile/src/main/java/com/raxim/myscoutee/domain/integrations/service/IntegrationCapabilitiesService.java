package com.raxim.myscoutee.domain.integrations.service;


import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.accounts.api.AccountIdentity;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationConnectDto;
import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService.AuthenticatedIntegrationClient;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;

/** Describes the authenticated integration, without probing any write endpoint. */
@Service
public class IntegrationCapabilitiesService {
    static final List<String> PERSONAL_READ_OPERATIONS = List.of(
            "listChats", "chatDetails", "chatMessages", "listNotifications");
    private final java.util.Optional<IntegrationProductCapabilities> products;
    private final IntegrationRoleAccessService roles;

    public IntegrationCapabilitiesService(java.util.Optional<IntegrationProductCapabilities> products,
            IntegrationRoleAccessService roles) {
        this.products = products;
        this.roles = roles;
    }

    public IntegrationConnectDto describe(Authentication authentication) {
        var profile = profile(authentication);
        var client = (AuthenticatedIntegrationClient) authentication.getDetails();
        String profileId = profile.getUserId();
        if (roles.privileged(profile)) {
            return new IntegrationConnectDto(true, IntegrationTokenService.MAX_BATCH_SIZE,
                    profileId, profile.getName(), null, null, List.of(), List.of(), client.accessMode());
        }
        if (products.isPresent()) return products.get().describe(profile, client.accessMode());
        return new IntegrationConnectDto(true, IntegrationTokenService.MAX_BATCH_SIZE,
                profileId, profile.getName(), org.springframework.util.StringUtils.hasText(profile.getWorkspaceGroupId())
                        ? profile.getWorkspaceGroupId() : null, null,
                client.accessMode().allowsRead() ? PERSONAL_READ_OPERATIONS : List.of(), List.of(), client.accessMode());
    }

    public String requireRead(Authentication authentication, String domain) {
        String actor = requireWrite(authentication, domain);
        if (!((AuthenticatedIntegrationClient) authentication.getDetails()).accessMode().allowsRead())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Read or Full integration access is required.");
        return actor;
    }

    public String requireWrite(Authentication authentication, String domain) {
        var profile = profile(authentication);
        if (roles.privileged(profile)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role integrations are read-only.");
        if (domain != null) {
            products.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN)).requireDomain(profile, domain);
        }
        return profile.getUserId();
    }

    public AuthenticatedIntegrationClient requireClient(Authentication authentication) {
        profile(authentication);
        return (AuthenticatedIntegrationClient) authentication.getDetails();
    }

    public String requireRoleRead(Authentication authentication, String role) {
        var profile = profile(authentication);
        var client = (AuthenticatedIntegrationClient) authentication.getDetails();
        if (!client.accessMode().allowsRead() || !("admin".equals(role) ? roles.admin(profile) : roles.operator(profile)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return profile.getUserId();
    }

    private WorkspaceProfile profile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountIdentity principal)
                || principal.getUser() == null
                || !(authentication.getDetails() instanceof AuthenticatedIntegrationClient client)
                || !client.ownerUserId().equals(principal.getUser().getUserId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (client.accessMode() == IntegrationAccess.Mode.BLOCKED) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        roles.requireActive(principal.getUser());
        return principal.getUser();
    }
}
