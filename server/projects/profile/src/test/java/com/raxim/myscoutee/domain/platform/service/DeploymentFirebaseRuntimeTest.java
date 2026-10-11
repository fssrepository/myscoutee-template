package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

import com.raxim.myscoutee.domain.platform.service.OperatorConfigurationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.repository.DeploymentConfigurationRepository;

@org.junit.jupiter.api.Tag("standard")
class DeploymentFirebaseRuntimeTest {
    @AfterEach
    void clearMode() {
        MongoSessionModeContext.clear();
    }

    @Test
    void publishesOnlyTheExplicitlyActivatedRealDeploymentCapability() {
        DeploymentConfiguration configuration =
                new DeploymentConfiguration();
        configuration.setId(DeploymentConfiguration.SINGLETON_ID);
        DeploymentConfiguration.Firebase firebase = runtimeReadyFirebase(
                encryptedSecret("authentication"),
                encryptedSecret("messaging"));
        configuration.setFirebase(firebase);
        DeploymentConfigurationRepository repository =
                mock(DeploymentConfigurationRepository.class);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID))
                .thenReturn(Optional.of(configuration));
        ConfigProperties properties = new ConfigProperties();
        DeploymentFirebaseRuntime runtime = new DeploymentFirebaseRuntime(
                repository,
                mock(DeploymentConfigurationCrypto.class),
                properties);

        runtime.initializeAvailability();

        assertThat(properties.isStartupFirebaseEnabled()).isFalse();
        assertThat(properties.isFirebaseEnabled()).isTrue();
    }

    @Test
    void demoModeNeverResolvesARealAuthenticationCredential() {
        DeploymentConfigurationRepository repository =
                mock(DeploymentConfigurationRepository.class);
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(true);
        DeploymentFirebaseRuntime runtime = new DeploymentFirebaseRuntime(
                repository,
                mock(DeploymentConfigurationCrypto.class),
                properties);
        MongoSessionModeContext.set(MongoSessionMode.DEMO);

        assertThatThrownBy(runtime::authentication)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Explore/demo");
        verifyNoInteractions(repository);
    }

    @Test
    void activatedDeploymentCredentialOverridesTheLegacyStartupApp() {
        EncryptedSecret envelope =
                encryptedSecret("authentication");
        DeploymentConfiguration.Firebase firebase = runtimeReadyFirebase(
                envelope,
                encryptedSecret("messaging"));
        DeploymentConfiguration configuration =
                new DeploymentConfiguration();
        configuration.setId(DeploymentConfiguration.SINGLETON_ID);
        configuration.setFirebase(firebase);
        DeploymentConfigurationRepository repository =
                mock(DeploymentConfigurationRepository.class);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID))
                .thenReturn(Optional.of(configuration));
        DeploymentConfigurationCrypto crypto =
                mock(DeploymentConfigurationCrypto.class);
        when(crypto.decrypt(
                OperatorConfigurationService
                        .FIREBASE_AUTHENTICATION_PURPOSE,
                envelope))
                .thenReturn("{}");
        ConfigProperties properties = new ConfigProperties();
        properties.setFirebaseEnabled(true);
        DeploymentFirebaseRuntime runtime = new DeploymentFirebaseRuntime(
                repository,
                crypto,
                properties);

        assertThatThrownBy(runtime::authentication)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Activated Firebase credential");
        verify(crypto).decrypt(
                OperatorConfigurationService
                        .FIREBASE_AUTHENTICATION_PURPOSE,
                envelope);
    }

    @Test
    void unverifiedActiveRevisionIsNeverPublished() {
        DeploymentConfiguration.Firebase firebase = runtimeReadyFirebase(
                encryptedSecret("authentication"),
                encryptedSecret("messaging"));
        firebase.setMessagingTestedRevision(6L);
        DeploymentConfiguration configuration =
                new DeploymentConfiguration();
        configuration.setId(DeploymentConfiguration.SINGLETON_ID);
        configuration.setFirebase(firebase);
        DeploymentConfigurationRepository repository =
                mock(DeploymentConfigurationRepository.class);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID))
                .thenReturn(Optional.of(configuration));
        ConfigProperties properties = new ConfigProperties();
        DeploymentFirebaseRuntime runtime = new DeploymentFirebaseRuntime(
                repository,
                mock(DeploymentConfigurationCrypto.class),
                properties);

        runtime.initializeAvailability();

        assertThat(properties.isFirebaseEnabled()).isFalse();
        assertThatThrownBy(runtime::authentication)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete or unverified");
    }

    @Test
    void remoteConfigurationSignalRefreshesActivationAndDeactivation() {
        var config = new DeploymentConfiguration();
        var firebase = runtimeReadyFirebase(encryptedSecret("authentication"), encryptedSecret("messaging"));
        config.setFirebase(firebase);
        var repository = mock(DeploymentConfigurationRepository.class);
        when(repository.findById(DeploymentConfiguration.SINGLETON_ID)).thenReturn(Optional.of(config));
        var properties = new ConfigProperties();
        var runtime = new DeploymentFirebaseRuntime(repository, mock(DeploymentConfigurationCrypto.class), properties);
        var changes = mock(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService.class);
        runtime.watchConfiguration(changes);
        var callback = org.mockito.ArgumentCaptor.forClass(java.util.function.Supplier.class);
        verify(changes).watch(org.mockito.ArgumentMatchers.eq("firebase-runtime"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), callback.capture());
        callback.getValue().get();
        assertThat(properties.isFirebaseEnabled()).isTrue();
        firebase.setActive(false);
        callback.getValue().get();
        assertThat(properties.isFirebaseEnabled()).isFalse();
        runtime.close();
    }

    private DeploymentConfiguration.Firebase runtimeReadyFirebase(
            EncryptedSecret authentication,
            EncryptedSecret messaging) {
        DeploymentConfiguration.Firebase firebase =
                new DeploymentConfiguration.Firebase();
        firebase.setProjectId("activated-project");
        firebase.setRevision(7L);
        firebase.setActive(true);
        firebase.setAuthenticationTestedRevision(7L);
        firebase.setMessagingTestedRevision(7L);
        firebase.setAuthenticationCredentialEnvelope(authentication);
        firebase.setMessagingCredentialEnvelope(messaging);
        DeploymentConfiguration.FirebasePublicClient publicClient =
                new DeploymentConfiguration.FirebasePublicClient();
        publicClient.setApiKey("browser-key");
        publicClient.setAuthDomain("activated-project.firebaseapp.com");
        publicClient.setMessagingSenderId("123456789");
        publicClient.setAppId("1:123456789:web:runtime");
        publicClient.setVapidKey("public-vapid-key");
        firebase.setPublicClient(publicClient);
        return firebase;
    }

    private EncryptedSecret encryptedSecret(
            String suffix) {
        EncryptedSecret envelope =
                new EncryptedSecret();
        envelope.setAlgorithm("AES-256-GCM");
        envelope.setKeyId("test-key");
        envelope.setNonce("test-nonce-" + suffix);
        envelope.setCiphertext("test-ciphertext-" + suffix);
        return envelope;
    }
}
