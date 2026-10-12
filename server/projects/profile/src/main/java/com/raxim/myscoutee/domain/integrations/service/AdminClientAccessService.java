package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.administration.api.AdministratorClassification;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;

@Service
public class AdminClientAccessService {
    private final ProfileDirectory users;
    private final AdministratorClassification admins;
    public AdminClientAccessService(ProfileDirectory users, AdministratorClassification admins) { this.users = users; this.admins = admins; }
    public WorkspaceProfile requireAdmin(String id) {
        WorkspaceProfile user = users.findByUserId(id).orElse(null);
        if (!admins.isAdminUser(user) || user == null || org.springframework.util.StringUtils.hasText(user.getDeletedAtIso())
                || java.util.Set.of("blocked", "deleted", "inactive").contains(String.valueOf(user.getProfileStatus()).toLowerCase())
                || java.util.Set.of("B", "D", "I").contains(String.valueOf(user.getStatus())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active administrator is required.");
        return user;
    }
}
