package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;

/** Additional checks and display data for an MCP connection to a workspace profile. */
public interface McpWorkspaceAccess {
    void requireProfile(String owner, WorkspaceProfile profile);
    String name(String workspaceId);
}
