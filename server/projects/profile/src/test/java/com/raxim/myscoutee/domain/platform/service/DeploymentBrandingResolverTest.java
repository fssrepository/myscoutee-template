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
    void absentSeedDoesNotInventAProductOrLogo() {
        var repository = org.mockito.Mockito.mock(DeploymentConfigurationRepository.class);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID)).thenReturn(Optional.empty());
        var resolved = new DeploymentBrandingResolver(repository).resolve();
        assertThat(resolved.productName()).isEmpty();
        assertThat(resolved.logoUrl()).isEmpty();
        assertThat(resolved.applicationIcon(null)).isEmpty();
    }

    @Test
    void savedEmptyLogoRemainsEmpty() {
        var repository = org.mockito.Mockito.mock(DeploymentConfigurationRepository.class);
        var configuration = new DeploymentConfiguration();
        var branding = new DeploymentConfiguration.Branding();
        branding.setProductName("Community Hub");
        branding.setLogoUrl("");
        configuration.setBranding(branding);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID)).thenReturn(Optional.of(configuration));
        var resolved = new DeploymentBrandingResolver(repository).resolve();
        assertThat(resolved.productName()).isEqualTo("Community Hub");
        assertThat(resolved.logoUrl()).isEmpty();
        assertThat(resolved.applicationIcon("/assets/logo/heart.webp")).isEmpty();
        assertThat(resolved.applicationIcon("/media/sender.webp")).isEqualTo("/media/sender.webp");
    }

    @Test
    void aFailedReadIsNotReplacedWithAnEarlierBrand() {
        var repository = org.mockito.Mockito.mock(DeploymentConfigurationRepository.class);
        var configuration = new DeploymentConfiguration();
        var branding = new DeploymentConfiguration.Branding();
        branding.setProductName("Earlier brand");
        configuration.setBranding(branding);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID))
                .thenReturn(Optional.of(configuration))
                .thenThrow(new IllegalStateException("temporarily unavailable"));
        var resolver = new DeploymentBrandingResolver(repository);
        assertThat(resolver.resolve().productName()).isEqualTo("Earlier brand");
        org.assertj.core.api.Assertions.assertThatThrownBy(resolver::resolve)
                .isInstanceOf(IllegalStateException.class).hasMessage("temporarily unavailable");
    }
}
