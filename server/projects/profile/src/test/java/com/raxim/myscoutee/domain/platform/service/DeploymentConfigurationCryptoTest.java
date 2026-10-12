package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.raxim.myscoutee.domain.platform.config.OperatorConfigurationProperties;
import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;

@org.junit.jupiter.api.Tag("standard")
class DeploymentConfigurationCryptoTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsAnAuthenticatedEnvelopeWithoutPersistingPlaintext() throws Exception {
        Path keyPath = tempDir.resolve("deployment-master-key");
        Files.writeString(
                keyPath,
                "000102030405060708090a0b0c0d0e0f"
                        + "101112131415161718191a1b1c1d1e1f\n");
        OperatorConfigurationProperties properties = new OperatorConfigurationProperties();
        properties.setMasterKeyPath(keyPath);
        DeploymentConfigurationCrypto crypto =
                new DeploymentConfigurationCrypto(properties);

        var envelope = crypto.encrypt(OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE, "test-authentication-secret");

        assertThat(envelope.getAlgorithm()).isEqualTo("AES-256-GCM");
        assertThat(envelope.getKeyId()).startsWith("sha256:");
        assertThat(envelope.getNonce()).isNotBlank();
        assertThat(envelope.getCiphertext())
                .isNotBlank()
                .doesNotContain("test-authentication-secret");
        assertThat(crypto.decrypt(OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE, envelope))
                .isEqualTo("test-authentication-secret");
        assertThatThrownBy(() -> crypto.decrypt("firebase-credential", envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be decrypted");
    }

    @Test
    void reportsTheExactDedicatedKeyGap() {
        Path keyPath = tempDir.resolve("missing-deployment-master-key");
        OperatorConfigurationProperties properties = new OperatorConfigurationProperties();
        properties.setMasterKeyPath(keyPath);
        DeploymentConfigurationCrypto crypto =
                new DeploymentConfigurationCrypto(properties);

        assertThat(crypto.unavailableReason())
                .contains("Operator configuration master key is not readable")
                .contains(keyPath.toString());
    }

    @Test
    void detectsTamperedCiphertextAndWrongKeyBeforeReportingCapabilityAvailable()
            throws Exception {
        DeploymentConfigurationCrypto crypto = crypto(
                tempDir.resolve("first-key"),
                "000102030405060708090a0b0c0d0e0f"
                        + "101112131415161718191a1b1c1d1e1f\n");
        var envelope = crypto.encrypt(
                OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE,
                "operator-secret");
        byte[] ciphertext = Base64.getDecoder().decode(envelope.getCiphertext());
        ciphertext[ciphertext.length - 1] ^= 0x01;
        envelope.setCiphertext(Base64.getEncoder().encodeToString(ciphertext));
        DeploymentConfiguration configuration = configuration(envelope);

        assertThat(crypto.unavailableReason(configuration))
                .contains("could not be decrypted");

        envelope = crypto.encrypt(
                OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE,
                "operator-secret");
        configuration = configuration(envelope);
        DeploymentConfigurationCrypto wrongKey = crypto(
                tempDir.resolve("second-key"),
                "f0e0d0c0b0a090807060504030201000"
                        + "0f1e2d3c4b5a69788796a5b4c3d2e1f0\n");

        assertThat(wrongKey.unavailableReason(configuration))
                .contains("different or unsupported master key");
    }

    @Test
    void scopedPortsReadExistingEnvelopesWithoutCrossingPurposeBoundaries() throws Exception {
        var crypto = crypto(tempDir.resolve("purpose-key"),
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        var email = crypto.encrypt("operator-email-provider", "email-test-secret");
        var unsubscribe = crypto.encrypt("email-unsubscribe-v1", "unsubscribe-test-secret");
        var authentication = crypto.encrypt(
                OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE,
                "authentication-test-secret");
        var providerPurpose = com.raxim.myscoutee.domain.platform.api.EmailSecrets.Purpose.PROVIDER;
        var unsubscribePurpose = com.raxim.myscoutee.domain.platform.api.EmailSecrets.Purpose.UNSUBSCRIBE;

        assertThat(crypto.decryptEmailSecret(providerPurpose, email)).isEqualTo("email-test-secret");
        assertThat(crypto.decryptEmailSecret(unsubscribePurpose, unsubscribe)).isEqualTo("unsubscribe-test-secret");
        assertThat(crypto.decrypt(OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE, authentication)).isEqualTo("authentication-test-secret");
        assertThat(crypto.decrypt("operator-email-provider",
                crypto.encryptEmailSecret(providerPurpose, "new-email-secret"))).isEqualTo("new-email-secret");
        assertThat(crypto.decryptEmailSecret(providerPurpose, null)).isNull();
        assertThatThrownBy(() -> crypto.decryptEmailSecret(providerPurpose, authentication))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decryptEmailSecret(providerPurpose, unsubscribe))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(OperatorConfigurationService.FIREBASE_AUTHENTICATION_PURPOSE, email))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(OperatorConfigurationService.FIREBASE_MESSAGING_PURPOSE, authentication))
                .isInstanceOf(IllegalStateException.class);
    }

    private DeploymentConfigurationCrypto crypto(
            Path path,
            String key) throws Exception {
        Files.writeString(path, key);
        OperatorConfigurationProperties properties =
                new OperatorConfigurationProperties();
        properties.setMasterKeyPath(path);
        return new DeploymentConfigurationCrypto(properties);
    }

    private DeploymentConfiguration configuration(
            EncryptedSecret envelope) {
        DeploymentConfiguration configuration = new DeploymentConfiguration();
        DeploymentConfiguration.Firebase firebase = new DeploymentConfiguration.Firebase();
        firebase.setAuthenticationCredentialEnvelope(envelope);
        configuration.setFirebase(firebase);
        return configuration;
    }
}
