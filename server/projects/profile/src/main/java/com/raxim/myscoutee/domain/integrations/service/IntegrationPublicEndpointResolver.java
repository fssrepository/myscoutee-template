package com.raxim.myscoutee.domain.integrations.service;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.platform.api.DeploymentSettings;

@Service
public class IntegrationPublicEndpointResolver {
    private final DeploymentSettings repository;
    private final String fallbackBaseUrl;

    public IntegrationPublicEndpointResolver(
            DeploymentSettings repository,
            @Value("${integration.public-base-url:http://localhost/api/integrations/v1}")
            String fallbackBaseUrl) {
        this.repository = repository;
        this.fallbackBaseUrl = requirePublicBaseUrl(fallbackBaseUrl);
    }

    public String resolve() {
        String configured = repository
                .findById(DeploymentSettings.SINGLETON_ID)
                .map(DeploymentSettings.Configuration::getIntegration)
                .map(DeploymentSettings.Integration::getPublicBaseUrl)
                .orElse(null);
        return StringUtils.hasText(configured)
                ? requirePublicBaseUrl(configured)
                : fallbackBaseUrl;
    }

    private static String requirePublicBaseUrl(String value) {
        String normalized = value == null
                ? ""
                : value.trim().transform(edgeText -> org.springframework.util.StringUtils.trimTrailingCharacter(edgeText, '/'));
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(uri.getHost())
                    && !StringUtils.hasText(uri.getUserInfo())) {
                return normalized;
            }
        } catch (IllegalArgumentException ignored) {
            // Rejected below with one stable configuration error.
        }
        throw new IllegalStateException(
                "Integration public base URL must be an absolute HTTP(S) URL.");
    }
}
