package com.raxim.myscoutee.domain.accounts.api;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;
public interface AccountIntegrationAccess { long updateIntegrationAccess(String owner, long revision, IntegrationAccess value); }
