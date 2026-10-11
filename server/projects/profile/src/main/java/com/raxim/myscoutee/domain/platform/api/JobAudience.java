package com.raxim.myscoutee.domain.platform.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface JobAudience {
    WorkspaceScope workspaceScope();
    record WorkspaceScope(java.util.Set<String> ids, boolean included) {
        public boolean matches(String workspaceId) {
            return included == (workspaceId != null && ids.contains(workspaceId));
        }
    }

    String forUser(String userId);
    String forTarget(String type, String id);
    default boolean matchesWorkspace(String workspaceId) {
        return !JobGroupContext.isActive() || workspaceScope().matches(workspaceId);
    }
    Map<String, List<String>> usersByGroup(Collection<String> userIds);
}
