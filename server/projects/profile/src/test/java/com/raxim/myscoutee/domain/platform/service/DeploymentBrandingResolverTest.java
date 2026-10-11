package com.raxim.myscoutee.domain.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.repository.DeploymentConfigurationRepository;

@org.junit.jupiter.api.Tag("standard")
class DeploymentBrandingResolverTest {
    @Test
    void resolvesDeploymentBrandingAndTemplateVariables() {
        DeploymentConfigurationRepository repository =
                org.mockito.Mockito.mock(
                        DeploymentConfigurationRepository.class);
        DeploymentConfiguration configuration =
                new DeploymentConfiguration();
        DeploymentConfiguration.Branding branding =
                new DeploymentConfiguration.Branding();
        branding.setProductName("Community Hub");
        branding.setLogoUrl("/api/assets/community.webp");
        branding.setRevision(7L);
        configuration.setBranding(branding);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID))
                .thenReturn(Optional.of(configuration));

        DeploymentBrandingResolver.ResolvedDeploymentBranding resolved =
                new DeploymentBrandingResolver(repository).resolve();

        assertThat(resolved.productName()).isEqualTo("Community Hub");
        assertThat(resolved.logoUrl())
                .isEqualTo("/api/assets/community.webp");
        assertThat(resolved.revision()).isEqualTo(7L);
        assertThat(resolved.enrich(Map.of("event_title", "Picnic")))
                .containsEntry("event_title", "Picnic")
                .containsEntry("product_name", "Community Hub")
                .containsEntry(
                        "product_logo_url",
                        "/api/assets/community.webp")
                .containsEntry("product_branding_revision", "7");
    }

    @Test
    void replacesOnlyBlankOrLegacyApplicationIcons() {
        DeploymentBrandingResolver.ResolvedDeploymentBranding branding =
                new DeploymentBrandingResolver.ResolvedDeploymentBranding(
                        "Community Hub",
                        "/api/assets/community.webp",
                        2L);

        assertThat(branding.applicationIcon(null))
                .isEqualTo("/api/assets/community.webp");
        assertThat(branding.applicationIcon(
                "/assets/logo/heart.webp"))
                .isEqualTo("/api/assets/community.webp");
        assertThat(branding.applicationIcon(
                "assets/logo/heart.png"))
                .isEqualTo("/api/assets/community.webp");
        assertThat(branding.applicationIcon(
                "/api/assets/event-cover.webp"))
                .isEqualTo("/api/assets/event-cover.webp");
    }

    @Test
    void fallsBackWhenTheSeedIsUnavailable() {
        DeploymentConfigurationRepository repository =
                org.mockito.Mockito.mock(
                        DeploymentConfigurationRepository.class);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID))
                .thenThrow(new IllegalStateException("temporarily unavailable"));

        DeploymentBrandingResolver.ResolvedDeploymentBranding resolved =
                new DeploymentBrandingResolver(repository).resolve();

        assertThat(resolved)
                .isEqualTo(com.raxim.myscoutee.domain.platform.api.DeploymentBranding.fallback());
    }

    @Test
    void preservesTheLastKnownBrandDuringATransientReadFailure() {
        DeploymentConfigurationRepository repository =
                org.mockito.Mockito.mock(
                        DeploymentConfigurationRepository.class);
        DeploymentConfiguration configuration =
                new DeploymentConfiguration();
        DeploymentConfiguration.Branding branding =
                new DeploymentConfiguration.Branding();
        branding.setProductName("Community Hub");
        branding.setLogoUrl("/api/assets/community.webp");
        branding.setRevision(9L);
        configuration.setBranding(branding);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID))
                .thenReturn(Optional.of(configuration))
                .thenThrow(new IllegalStateException(
                        "temporarily unavailable"));
        DeploymentBrandingResolver resolver =
                new DeploymentBrandingResolver(repository);

        DeploymentBrandingResolver.ResolvedDeploymentBranding first =
                resolver.resolve();
        DeploymentBrandingResolver.ResolvedDeploymentBranding second =
                resolver.resolve();

        assertThat(first.productName()).isEqualTo("Community Hub");
        assertThat(second).isEqualTo(first);
    }
    @Test
    void failureFallbackNeverLeaksBrandingFromAnotherDatabase() {
        var repository = org.mockito.Mockito.mock(DeploymentConfigurationRepository.class);
        var config = new DeploymentConfiguration();
        var branding = new DeploymentConfiguration.Branding(); branding.setProductName("Real brand"); config.setBranding(branding);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID)).thenReturn(Optional.of(config))
                .thenThrow(new IllegalStateException("offline"));
        var resolver = new DeploymentBrandingResolver(repository);
        try {
            com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.set(com.raxim.myscoutee.common.config.mongo.MongoSessionMode.REAL);
            assertThat(resolver.resolve().productName()).isEqualTo("Real brand");
            com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.set(com.raxim.myscoutee.common.config.mongo.MongoSessionMode.DEMO);
            assertThat(resolver.resolve()).isEqualTo(com.raxim.myscoutee.domain.platform.api.DeploymentBranding.fallback());
        } finally { com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.clear(); }
    }

}
