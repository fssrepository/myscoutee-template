package com.raxim.myscoutee.domain.integrations.service;

import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationConnectDto;

/** Product-specific operation discovery and workspace restrictions. */
interface IntegrationProductCapabilities {
    IntegrationConnectDto describe(WorkspaceProfile profile, IntegrationAccess.Mode mode);
    void requireDomain(WorkspaceProfile profile, String domain);
}
