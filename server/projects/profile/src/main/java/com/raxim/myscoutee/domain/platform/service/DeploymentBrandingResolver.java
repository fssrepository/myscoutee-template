package com.raxim.myscoutee.domain.platform.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.repository.DeploymentConfigurationRepository;

@Service
public class DeploymentBrandingResolver implements com.raxim.myscoutee.domain.platform.api.DeploymentBranding {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(DeploymentBrandingResolver.class);
    private final DeploymentConfigurationRepository repository;
    private final java.util.concurrent.ConcurrentMap<com.raxim.myscoutee.common.config.mongo.MongoSessionMode, ResolvedDeploymentBranding> lastKnownGood =
            new java.util.concurrent.ConcurrentHashMap<>();

    public DeploymentBrandingResolver(
            DeploymentConfigurationRepository repository) {
        this.repository = repository;
    }

    public ResolvedDeploymentBranding resolve() {
        try {
            DeploymentConfiguration configuration = repository
                    .findById(DeploymentConfiguration.SINGLETON_ID)
                    .orElse(null);
            DeploymentConfiguration.Branding branding =
                    configuration == null
                            ? null
                            : configuration.getBranding();
            ResolvedDeploymentBranding resolved =
                    new ResolvedDeploymentBranding(
                            firstNonBlank(
                                    branding == null
                                            ? null
                                            : branding.getProductName(),
                                    DEFAULT_PRODUCT_NAME),
                            firstNonBlank(
                                    branding == null
                                            ? null
                                            : branding.getLogoUrl(),
                                    DEFAULT_LOGO_URL),
                            branding == null
                                            || branding.getRevision() == null
                                    ? 0L
                                    : Math.max(
                                            0L,
                                            branding.getRevision()));
            lastKnownGood.put(com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode(), resolved);
            return resolved;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not resolve deployment branding; using the "
                            + "last known good snapshot.",
                    exception);
            return lastKnownGood.getOrDefault(com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode(),
                    com.raxim.myscoutee.domain.platform.api.DeploymentBranding.fallback());
        }
    }

    private static String firstNonBlank(String first, String second) {
        String normalized = safeText(first);
        return StringUtils.hasText(normalized)
                ? normalized
                : safeText(second);
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

}
