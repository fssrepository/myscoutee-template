package com.raxim.myscoutee.domain.platform.api;

import java.util.Map;

/** Workspace ownership used by shared background jobs. */
public interface JobWorkspaceDirectory {
    Map<String, String> workspaceBaseIds();
    String jobGroupForWorkspace(String workspaceId);
}
