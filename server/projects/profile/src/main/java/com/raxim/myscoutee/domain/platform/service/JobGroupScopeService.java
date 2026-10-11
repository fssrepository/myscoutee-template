package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

import com.raxim.myscoutee.domain.platform.api.JobTargetResolver;
import com.raxim.myscoutee.domain.platform.api.JobWorkspaceDirectory;
import java.util.Optional;
import java.util.Map;


import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;

/** Resolves stored workspace ownership at job boundaries; never accepts an admin-supplied scope. */
@Service
public class JobGroupScopeService implements com.raxim.myscoutee.domain.platform.api.JobAudience {
    private final com.raxim.myscoutee.domain.accounts.api.ProfileDirectory profiles;
    private final Optional<JobWorkspaceDirectory> workspaceDirectory;
    private final Map<String, JobTargetResolver> targetResolvers;

    public JobGroupScopeService(com.raxim.myscoutee.domain.accounts.api.ProfileDirectory profiles,
            Optional<JobWorkspaceDirectory> workspaceDirectory, List<JobTargetResolver> targetResolvers) {
        this.profiles = profiles;
        this.workspaceDirectory = workspaceDirectory;
        var byType = new java.util.HashMap<String, JobTargetResolver>();
        for (JobTargetResolver resolver : targetResolvers) {
            String type = resolver.jobTargetType();
            if (type == null || type.isBlank() || "user".equals(type) || byType.putIfAbsent(type, resolver) != null) {
                throw new IllegalStateException("Invalid or duplicate job target resolver: " + type);
            }
        }
        this.targetResolvers = Map.copyOf(byType);
    }

    public String forWorkspace(String workspaceId) {
        return workspaceDirectory.map(directory -> directory.jobGroupForWorkspace(workspaceId)).orElse(null);
    }

    public java.util.Map<String, List<String>> usersByGroup(java.util.Collection<String> userIds) {
        var profiles = this.profiles.findByUserIdIn(userIds);
        var byId = new java.util.HashMap<String, WorkspaceProfile>();
        profiles.forEach(profile -> byId.put(profile.getUserId(), profile));
        var workspaces = workspaces();
        var result = new java.util.LinkedHashMap<String, List<String>>();
        for (String userId : userIds) {
            var user = byId.get(userId);
            String workspace = user == null ? null : user.getWorkspaceGroupId();
            String group = workspaces.get(workspace);
            result.computeIfAbsent(group, ignored -> new ArrayList<>()).add(userId);
        }
        return result;
    }

    public String forUser(String userId) {
        var user = profiles.findFirstByUserId(userId);
        return user == null ? JobGroupContext.groupId() : forWorkspace(user.getWorkspaceGroupId());
    }

    public String forTarget(String type, String id) {
        type = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        if ("user".equals(type)) return forUser(id);
        JobTargetResolver resolver = targetResolvers.get(type);
        return resolver == null ? JobGroupContext.groupId()
                : resolver.jobGroupForTarget(id, this::forWorkspace, this::forUser);
    }

    private java.util.Map<String, String> workspaces() {
        return JobGroupContext.memo("workspaceBaseIds",
                () -> workspaceDirectory.isPresent() ? workspaceDirectory.get().workspaceBaseIds()
                        : java.util.Collections.emptyMap());
    }

    @Override public WorkspaceScope workspaceScope() {
        var spaces = workspaces();
        String scope = JobGroupContext.groupId();
        return scope == null ? new WorkspaceScope(java.util.Set.copyOf(spaces.keySet()), false)
                : new WorkspaceScope(spaces.entrySet().stream().filter(entry -> scope.equals(entry.getValue()))
                    .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()), true);
    }


}
