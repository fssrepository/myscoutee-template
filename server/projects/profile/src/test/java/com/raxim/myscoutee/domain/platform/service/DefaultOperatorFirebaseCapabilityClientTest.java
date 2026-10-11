package com.raxim.myscoutee.domain.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.google.firebase.FirebaseApp;
import com.raxim.myscoutee.domain.platform.service.OperatorFirebaseCapabilityClient.FirebaseWebConfiguration;

import tools.jackson.databind.ObjectMapper;

@org.junit.jupiter.api.Tag("standard")
class DefaultOperatorFirebaseCapabilityClientTest {
    private static final String PROJECT_ID = "operator-capability-project";

    @Test
    void authenticationCheckUsesAndDeletesOnlyAnIsolatedNamedApp()
            throws Exception {
        List<String> appNamesBefore = FirebaseApp.getApps().stream()
                .map(FirebaseApp::getName)
                .sorted()
                .toList();
        AtomicReference<String> isolatedAppName = new AtomicReference<>();
        var client = new DefaultOperatorFirebaseCapabilityClient(
                new DefaultOperatorFirebaseCapabilityClient
                        .FirebaseCapabilityOperations() {
                    @Override
                    public void testAuthentication(FirebaseApp app) {
                        isolatedAppName.set(app.getName());
                        assertThat(app.getOptions().getProjectId())
                                .isEqualTo(PROJECT_ID);
                        assertThat(app.getName())
                                .startsWith("operator-capability-");
                    }

                    @Override
                    public String sendTestMessage(
                            FirebaseApp app,
                            String destinationToken,
                            String notificationTitle) {
                        throw new AssertionError("Unexpected messaging call.");
                    }
                });

        client.testAuthentication(
                PROJECT_ID,
                serviceAccountJson(PROJECT_ID),
                webConfiguration(""));

        assertThat(FirebaseApp.getApps().stream()
                .map(FirebaseApp::getName)
                .sorted()
                .toList())
                .as("capability checks must not leave or replace Firebase apps")
                .isEqualTo(appNamesBefore);
        assertThat(isolatedAppName).hasValueSatisfying(
                name -> assertThat(name)
                        .startsWith("operator-capability-"));
    }

    @Test
    void rejectsCredentialForAnotherProjectWithoutLeakingAnApp()
            throws Exception {
        List<String> appNamesBefore = FirebaseApp.getApps().stream()
                .map(FirebaseApp::getName)
                .sorted()
                .toList();
        var client = new DefaultOperatorFirebaseCapabilityClient();

        assertThatThrownBy(() -> client.testAuthentication(
                "expected-project",
                serviceAccountJson("different-project"),
                webConfiguration("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
        assertThat(FirebaseApp.getApps().stream()
                .map(FirebaseApp::getName)
                .sorted()
                .toList())
                .isEqualTo(appNamesBefore);
    }

    @Test
    void rejectsInvalidVapidKeyBeforeMessagingNetworkOperations()
            throws Exception {
        var client = new DefaultOperatorFirebaseCapabilityClient(
                new DefaultOperatorFirebaseCapabilityClient
                        .FirebaseCapabilityOperations() {
                    @Override
                    public void testAuthentication(FirebaseApp app) {
                        throw new AssertionError(
                                "Unexpected authentication call.");
                    }

                    @Override
                    public String sendTestMessage(
                            FirebaseApp app,
                            String destinationToken,
                            String notificationTitle) {
                        throw new AssertionError(
                                "Unexpected messaging call.");
                    }
                });

        assertThatThrownBy(() -> client.sendTestMessage(
                PROJECT_ID,
                serviceAccountJson(PROJECT_ID),
                webConfiguration("not-a-p256-public-key"),
                "browser-readiness-token",
                null,
                "Community"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VAPID public key is invalid");
    }

    @Test
    void validatesAuthorizedDomainAndSupportedSignInConfiguration()
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var configuration = mapper.readTree("""
                {
                  "authorizedDomains": [
                    "example.test",
                    "Login.Example.Test."
                  ],
                  "signIn": {
                    "email": {
                      "enabled": true,
                      "passwordRequired": false
                    }
                  }
                }
                """);
        var google = mapper.readTree("""
                {
                  "name": "projects/123/defaultSupportedIdpConfigs/google.com",
                  "enabled": true,
                  "clientId": "browser-client-id"
                }
                """);
        assertThat(
                DefaultOperatorFirebaseCapabilityClient.hasAuthorizedDomain(
                        configuration.path("authorizedDomains"),
                        "login.example.test"))
                .isTrue();
        assertThat(
                DefaultOperatorFirebaseCapabilityClient.emailPasswordEnabled(
                        configuration))
                .isTrue();
        assertThat(
                DefaultOperatorFirebaseCapabilityClient
                        .enabledGoogleProvider(google))
                .isTrue();
    }

    private FirebaseWebConfiguration webConfiguration(
            String vapidKey) {
        return new FirebaseWebConfiguration(
                "browser-api-key",
                PROJECT_ID + ".firebaseapp.com",
                PROJECT_ID + ".firebasestorage.app",
                "123456789",
                "1:123456789:web:operator",
                "G-OPERATOR",
                vapidKey);
    }

    private String serviceAccountJson(String projectId) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        byte[] privateKey = generator.generateKeyPair()
                .getPrivate()
                .getEncoded();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                        .encodeToString(privateKey)
                + "\n-----END PRIVATE KEY-----\n";
        Map<String, String> credential = new LinkedHashMap<>();
        credential.put("type", "service_account");
        credential.put("project_id", projectId);
        credential.put("private_key_id", "test-private-key");
        credential.put("private_key", pem);
        credential.put(
                "client_email",
                "operator-capability@" + projectId + ".iam.gserviceaccount.com");
        credential.put("client_id", "1234567890");
        credential.put(
                "auth_uri",
                "https://accounts.google.com/o/oauth2/auth");
        credential.put(
                "token_uri",
                "https://oauth2.googleapis.com/token");
        return new ObjectMapper().writeValueAsString(credential);
    }
}
