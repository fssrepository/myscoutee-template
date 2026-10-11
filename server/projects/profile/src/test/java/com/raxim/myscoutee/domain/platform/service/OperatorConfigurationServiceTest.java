package com.raxim.myscoutee.domain.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.config.OperatorConfigurationProperties;
import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.*;
import com.raxim.myscoutee.domain.platform.mapper.OperatorConfigurationMapper;
import com.raxim.myscoutee.domain.platform.repository.DeploymentConfigurationRepository;

/** Exercises the retained deployment flow with a genuinely payment-free document. */
@Tag("standard")
class OperatorConfigurationServiceTest {
    @TempDir Path directory;
    private DeploymentConfiguration configuration;
    private DeploymentConfigurationRepository repository;
    private AppSettingsService settings;
    private DeploymentConfigurationCrypto crypto;
    private OperatorFirebaseCapabilityClient firebase;
    private OperatorConfigurationService service;

    @BeforeEach void setup() throws Exception {
        MongoSessionModeContext.clear();
        configuration = new DeploymentConfiguration();
        configuration.setId(DeploymentConfiguration.SINGLETON_ID);
        configuration.setVersion(0L);
        configuration.setSchemaVersion(DeploymentConfiguration.SCHEMA_VERSION);
        var branding = new DeploymentConfiguration.Branding();
        branding.setProductName("Templates");
        branding.setHomeLabel("Templates");
        branding.setLogoUrl("");
        branding.setRevision(0L);
        configuration.setBranding(branding);
        configuration.setSocialLinks(List.of());
        var privacy = new DeploymentConfiguration.PrivacyContact();
        privacy.setDataControllerName("");
        privacy.setPrivacyContactEmail("");
        configuration.setPrivacyContact(privacy);
        configuration.setIntegration(new DeploymentConfiguration.Integration());
        var firebaseConfig = new DeploymentConfiguration.Firebase();
        firebaseConfig.setProjectId("");
        configuration.setFirebase(firebaseConfig);
        repository = mock(DeploymentConfigurationRepository.class);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID)).thenReturn(Optional.of(configuration));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        settings = mock(AppSettingsService.class);
        when(settings.adminEmails()).thenReturn(List.of("admin@example.test"));
        when(settings.updateAdminEmails(any())).thenAnswer(call -> call.getArgument(0));
        var properties = new OperatorConfigurationProperties();
        var key = directory.resolve("deployment-master-key");
        Files.writeString(key, "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        properties.setMasterKeyPath(key);
        crypto = new DeploymentConfigurationCrypto(properties);
        firebase = mock(OperatorFirebaseCapabilityClient.class);
        service = new OperatorConfigurationService(repository, settings,
                new OperatorConfigurationMapper(), crypto, Clock.systemUTC(), firebase);
    }

    @AfterEach void clearMode() { MongoSessionModeContext.clear(); }

    @Test void readsNativeConfigurationWithoutPaymentDataOrProviderQueries() {
        var result = service.configuration();
        assertThat(result.capability()).isEqualTo("AVAILABLE");
        assertThat(result.branding().productName()).isEqualTo("Templates");
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("payment");
        verify(repository).findById(DeploymentConfiguration.SINGLETON_ID);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(firebase);
    }

    @Test void savesGenericSettingsAndPreservesOmittedFirebaseConfiguration() {
        var originalFirebase = configuration.getFirebase();
        var result = service.update(update(null));
        assertThat(result.branding().productName()).isEqualTo("New project");
        assertThat(result.integration().publicBaseUrl()).isEqualTo("https://example.test/api/integrations/v1");
        assertThat(result.privacyContact().privacyContactEmail()).isEqualTo("privacy@example.test");
        assertThat(configuration.getFirebase()).isSameAs(originalFirebase);
        verify(repository).save(configuration);
        verifyNoInteractions(firebase);
    }

    @Test void encryptsCredentialsAndNeverReturnsTheirPlaintext() {
        var result = service.update(update(new FirebaseUpdateDto("project", "authentication-secret", "messaging-secret")));
        assertThat(crypto.decrypt(OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE,
                configuration.getFirebase().getAuthenticationCredentialEnvelope())).isEqualTo("authentication-secret");
        var json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("authentication-secret", "messaging-secret", "ciphertext", "payment");
        assertThat(result.firebase().authenticationCredentialConfigured()).isTrue();
        assertThat(result.firebase().active()).isFalse();
        assertThatThrownBy(service::activateFirebase).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test void demoCannotTestOrActivateLiveFirebase() {
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        assertThat(service.test(new ConfigurationTestRequestDto(ConfigurationTestKind.FIREBASE_AUTHENTICATION)).success()).isFalse();
        assertThatThrownBy(service::activateFirebase).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(repository, firebase);
    }

    @Test void preservesConcurrentUpdateRejection() {
        when(repository.save(any())).thenThrow(new OptimisticLockingFailureException("changed"));
        assertThatThrownBy(() -> service.update(update(null))).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(settings, never()).updateAdminEmails(any());
    }

    private OperatorConfigurationUpdateDto update(FirebaseUpdateDto firebaseUpdate) {
        return new OperatorConfigurationUpdateDto(List.of("admin@example.test"),
                new PrivacyContactUpdateDto("Example", "PRIVACY@example.test"), List.of(),
                new BrandingUpdateDto("New project", -1, ""),
                new IntegrationUpdateDto("https://example.test/api/integrations/v1"), firebaseUpdate);
    }
}
