package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.platform.api.AdministratorSettings;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;

/** Integration role checks use the same current role sources as interactive authentication. */
@Service
public class IntegrationRoleAccessService {
    private final AdministratorSettings settings;
    private final RuntimeConfiguration config;

    public IntegrationRoleAccessService(AdministratorSettings settings, RuntimeConfiguration config) {
        this.settings = settings;
        this.config = config;
    }

    public void requireActive(WorkspaceProfile user) {
        if (user == null || StringUtils.hasText(user.getDeletedAtIso())
                || java.util.Set.of("B", "D", "I").contains(String.valueOf(user.getStatus()))
                || java.util.Set.of("blocked", "deleted", "inactive").contains(
                    String.valueOf(user.getProfileStatus()).toLowerCase(java.util.Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public boolean admin(WorkspaceProfile user) {
        return base(user) && settings.isAdminEmail(user.getEmail());
    }

    public boolean operator(WorkspaceProfile user) {
        return base(user) && (user.isOperator() || config.isOperatorUser(user.getEmail()));
    }

    public boolean privileged(WorkspaceProfile user) { return admin(user) || operator(user); }

    private boolean base(WorkspaceProfile user) {
        return user != null && !StringUtils.hasText(user.getWorkspaceGroupId())
                && !StringUtils.hasText(user.getAccountUserId());
    }
}
