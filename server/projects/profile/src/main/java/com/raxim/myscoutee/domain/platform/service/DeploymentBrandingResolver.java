package com.raxim.myscoutee.domain.platform.service;

import org.springframework.stereotype.Service;

import com.raxim.myscoutee.domain.platform.api.DeploymentBranding;
import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.repository.DeploymentConfigurationRepository;

@Service
public class DeploymentBrandingResolver implements DeploymentBranding {
    private final DeploymentConfigurationRepository repository;

    public DeploymentBrandingResolver(DeploymentConfigurationRepository repository) {
        this.repository = repository;
    }

    public ResolvedDeploymentBranding resolve() {
        DeploymentConfiguration configuration = repository
                .findById(DeploymentConfiguration.SINGLETON_ID)
                .orElse(null);
        DeploymentConfiguration.Branding branding = configuration == null
                ? null : configuration.getBranding();
        return new ResolvedDeploymentBranding(
                safeText(branding == null ? null : branding.getProductName()),
                safeText(branding == null ? null : branding.getLogoUrl()),
                branding == null || branding.getRevision() == null
                        ? 0L : Math.max(0L, branding.getRevision()));
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
