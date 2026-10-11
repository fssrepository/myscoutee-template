package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

import com.raxim.myscoutee.domain.platform.service.OperatorConfigurationService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.repository.DeploymentConfigurationRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Resolves the explicitly activated deployment Firebase configuration for
 * live authentication and messaging. Operator capability tests continue to
 * use short-lived isolated apps and therefore cannot switch live traffic.
 */
@Service
public class DeploymentFirebaseRuntime implements com.raxim.myscoutee.domain.platform.api.FirebaseRuntime {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(DeploymentFirebaseRuntime.class);
    private static final String APP_NAME_PREFIX =
            "myscoutee-deployment-firebase-";

    private final DeploymentConfigurationRepository repository;
    private final DeploymentConfigurationCrypto crypto;
    private final ConfigProperties configProperties;
    private final Object appLock = new Object();
    private final AtomicLong appSequence = new AtomicLong();
    private RuntimeApp authenticationApp;
    private RuntimeApp messagingApp;

    public DeploymentFirebaseRuntime(
            DeploymentConfigurationRepository repository,
            DeploymentConfigurationCrypto crypto,
            ConfigProperties configProperties) {
        this.repository = Objects.requireNonNull(repository);
        this.crypto = Objects.requireNonNull(crypto);
        this.configProperties = Objects.requireNonNull(configProperties);
    }

    @org.springframework.beans.factory.annotation.Autowired
    void watchConfiguration(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes) {
        changes.watch("firebase-runtime", java.util.Set.of("deploymentConfigurations"), java.util.Set.of("firebase"), () -> {
            refreshAfterConfigurationChange(); return null;
        });
    }

    @PostConstruct
    public void initializeAvailability() {
        refreshAfterConfigurationChange();
    }

    public boolean isAuthenticationEnabled() {
        return configProperties.isStartupFirebaseEnabled()
                || configProperties.isFirebaseEnabled();
    }

    public FirebaseAuth authentication() {
        if (MongoSessionModeContext.currentMode()
                == MongoSessionMode.DEMO) {
            throw new IllegalStateException(
                    "Live Firebase authentication is unavailable in Explore/demo mode.");
        }
        synchronized (appLock) {
            ResolvedCredential resolved = resolve(
                    CredentialKind.AUTHENTICATION);
            if (resolved.legacyStartup()) {
                return FirebaseAuth.getInstance();
            }
            return FirebaseAuth.getInstance(runtimeApp(resolved));
        }
    }

    public FirebaseMessaging messaging() {
        if (MongoSessionModeContext.currentMode()
                == MongoSessionMode.DEMO) {
            throw new IllegalStateException(
                    "Live Firebase messaging is unavailable in Explore/demo mode.");
        }
        synchronized (appLock) {
            ResolvedCredential resolved = resolve(
                    CredentialKind.MESSAGING);
            if (resolved.legacyStartup()) {
                return FirebaseMessaging.getInstance();
            }
            return FirebaseMessaging.getInstance(runtimeApp(resolved));
        }
    }

    /**
     * Refreshes the public enabled signal and evicts runtime apps whose
     * activated revision or encrypted credential changed. Demo configuration
     * can never change live process state.
     */
    public void refreshAfterConfigurationChange() {
        if (MongoSessionModeContext.currentMode()
                == MongoSessionMode.DEMO) {
            return;
        }
        boolean active = false;
        try {
            DeploymentConfiguration.Firebase firebase =
                    repository.findById(
                            DeploymentConfiguration.SINGLETON_ID)
                    .map(DeploymentConfiguration::getFirebase)
                    .orElse(null);
            active = runtimeReady(firebase);
            if (active) {
                invalidateChangedRuntimeApps(firebase);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Unable to refresh deployment Firebase availability; "
                            + "the bootstrap login remains available.",
                    exception);
        }
        configProperties.publishDeploymentFirebaseEnabled(active);
        if (!active) {
            closeRuntimeApps();
        }
    }

    @PreDestroy
    public void close() {
        closeRuntimeApps();
    }

    private ResolvedCredential resolve(CredentialKind kind) {
        DeploymentConfiguration configuration = repository.findById(
                DeploymentConfiguration.SINGLETON_ID)
                .orElse(null);
        DeploymentConfiguration.Firebase firebase =
                configuration == null ? null : configuration.getFirebase();
        if (firebase == null || !firebase.isActive()) {
            if (configProperties.isStartupFirebaseEnabled()) {
                return ResolvedCredential.legacy();
            }
            throw new IllegalStateException(
                    "Firebase has not been activated for this deployment.");
        }
        if (!runtimeReady(firebase)) {
            throw new IllegalStateException(
                    "Activated Firebase revision is incomplete or unverified.");
        }
        long revision = firebase.getRevision() == null
                ? 0L
                : Math.max(0L, firebase.getRevision());
        String projectId = requireText(
                firebase.getProjectId(),
                "Activated Firebase project id is missing.");
        EncryptedSecret envelope =
                kind == CredentialKind.AUTHENTICATION
                        ? firebase.getAuthenticationCredentialEnvelope()
                        : firebase.getMessagingCredentialEnvelope();
        if (envelope == null) {
            throw new IllegalStateException(
                    "Activated Firebase credential is missing.");
        }
        String purpose = kind == CredentialKind.AUTHENTICATION
                ? OperatorConfigurationService
                        .FIREBASE_AUTHENTICATION_PURPOSE
                : OperatorConfigurationService
                        .FIREBASE_MESSAGING_PURPOSE;
        return new ResolvedCredential(
                kind,
                projectId,
                revision,
                credentialFingerprint(envelope),
                envelope,
                purpose,
                false);
    }

    private FirebaseApp runtimeApp(ResolvedCredential resolved) {
        synchronized (appLock) {
            RuntimeApp current =
                    resolved.kind() == CredentialKind.AUTHENTICATION
                            ? authenticationApp
                            : messagingApp;
            if (current != null && current.matches(resolved)) {
                return current.app();
            }
            FirebaseApp next = initializeApp(resolved);
            RuntimeApp replacement = new RuntimeApp(
                    resolved.kind(),
                    resolved.projectId(),
                    resolved.revision(),
                    resolved.credentialFingerprint(),
                    next);
            if (resolved.kind() == CredentialKind.AUTHENTICATION) {
                authenticationApp = replacement;
            } else {
                messagingApp = replacement;
            }
            delete(current);
            return next;
        }
    }

    private FirebaseApp initializeApp(ResolvedCredential resolved) {
        try {
            String serviceAccountJson = requireText(
                    crypto.decrypt(
                            resolved.purpose(),
                            resolved.credentialEnvelope()),
                    "Activated Firebase credential is unavailable.");
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(
                            serviceAccountJson
                                    .getBytes(StandardCharsets.UTF_8)));
            if (!(credentials
                    instanceof ServiceAccountCredentials serviceAccount)) {
                throw new IllegalStateException(
                        "Activated Firebase credential is not a service account.");
            }
            String credentialProjectId = serviceAccount.getProjectId();
            if (StringUtils.hasText(credentialProjectId)
                    && !resolved.projectId().equals(
                            credentialProjectId.trim())) {
                throw new IllegalStateException(
                        "Activated Firebase project does not match its service account.");
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(resolved.projectId())
                    .build();
            String appName = APP_NAME_PREFIX
                    + resolved.kind().name().toLowerCase(Locale.ROOT)
                    + "-"
                    + resolved.revision()
                    + "-"
                    + appSequence.incrementAndGet();
            return FirebaseApp.initializeApp(options, appName);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Activated Firebase credential is invalid.",
                    exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException) {
                throw exception;
            }
            throw new IllegalStateException(
                    "Activated Firebase runtime initialization failed.",
                    exception);
        }
    }

    private void closeRuntimeApps() {
        synchronized (appLock) {
            delete(authenticationApp);
            delete(messagingApp);
            authenticationApp = null;
            messagingApp = null;
        }
    }

    private void invalidateChangedRuntimeApps(
            DeploymentConfiguration.Firebase firebase) {
        synchronized (appLock) {
            RuntimeIdentity authenticationIdentity =
                    runtimeIdentity(firebase, CredentialKind.AUTHENTICATION);
            if (authenticationApp != null
                    && !authenticationApp.matches(authenticationIdentity)) {
                delete(authenticationApp);
                authenticationApp = null;
            }
            RuntimeIdentity messagingIdentity =
                    runtimeIdentity(firebase, CredentialKind.MESSAGING);
            if (messagingApp != null
                    && !messagingApp.matches(messagingIdentity)) {
                delete(messagingApp);
                messagingApp = null;
            }
        }
    }

    private RuntimeIdentity runtimeIdentity(
            DeploymentConfiguration.Firebase firebase,
            CredentialKind kind) {
        if (!runtimeReady(firebase)) {
            return null;
        }
        EncryptedSecret envelope =
                kind == CredentialKind.AUTHENTICATION
                        ? firebase.getAuthenticationCredentialEnvelope()
                        : firebase.getMessagingCredentialEnvelope();
        if (!StringUtils.hasText(firebase.getProjectId())
                || envelope == null) {
            return null;
        }
        long revision = firebase.getRevision() == null
                ? 0L
                : Math.max(0L, firebase.getRevision());
        return new RuntimeIdentity(
                kind,
                firebase.getProjectId().trim(),
                revision,
                credentialFingerprint(envelope));
    }

    private boolean runtimeReady(
            DeploymentConfiguration.Firebase firebase) {
        if (firebase == null || !firebase.isActive()) {
            return false;
        }
        long revision = firebase.getRevision() == null
                ? 0L
                : Math.max(0L, firebase.getRevision());
        DeploymentConfiguration.FirebasePublicClient publicClient =
                firebase.getPublicClient();
        return StringUtils.hasText(firebase.getProjectId())
                && firebase.getAuthenticationCredentialEnvelope() != null
                && firebase.getMessagingCredentialEnvelope() != null
                && publicClient != null
                && StringUtils.hasText(publicClient.getApiKey())
                && StringUtils.hasText(publicClient.getAuthDomain())
                && StringUtils.hasText(
                        publicClient.getMessagingSenderId())
                && StringUtils.hasText(publicClient.getAppId())
                && StringUtils.hasText(publicClient.getVapidKey())
                && Long.valueOf(revision).equals(
                        firebase.getAuthenticationTestedRevision())
                && Long.valueOf(revision).equals(
                        firebase.getMessagingTestedRevision());
    }

    private String credentialFingerprint(
            EncryptedSecret envelope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateFingerprint(digest, envelope.getAlgorithm());
            updateFingerprint(digest, envelope.getKeyId());
            updateFingerprint(digest, envelope.getNonce());
            updateFingerprint(digest, envelope.getCiphertext());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Firebase runtime fingerprinting is unavailable.",
                    exception);
        }
    }

    private void updateFingerprint(
            MessageDigest digest,
            String value) {
        byte[] bytes = Objects.toString(value, "")
                .getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private void delete(RuntimeApp runtimeApp) {
        if (runtimeApp == null) {
            return;
        }
        try {
            runtimeApp.app().delete();
        } catch (RuntimeException exception) {
            LOGGER.debug(
                    "Unable to delete a replaced Firebase runtime app.",
                    exception);
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private enum CredentialKind {
        AUTHENTICATION,
        MESSAGING
    }

    private record ResolvedCredential(
            CredentialKind kind,
            String projectId,
            long revision,
            String credentialFingerprint,
            EncryptedSecret credentialEnvelope,
            String purpose,
            boolean legacyStartup) {
        private static ResolvedCredential legacy() {
            return new ResolvedCredential(
                    CredentialKind.AUTHENTICATION,
                    "",
                    -1L,
                    "",
                    null,
                    "",
                    true);
        }
    }

    private record RuntimeApp(
            CredentialKind kind,
            String projectId,
            long revision,
            String credentialFingerprint,
            FirebaseApp app) {
        private boolean matches(ResolvedCredential resolved) {
            return matches(new RuntimeIdentity(
                    resolved.kind(),
                    resolved.projectId(),
                    resolved.revision(),
                    resolved.credentialFingerprint()));
        }

        private boolean matches(RuntimeIdentity identity) {
            return identity != null
                    && kind == identity.kind()
                    && projectId.equals(identity.projectId())
                    && revision == identity.revision()
                    && credentialFingerprint.equals(
                            identity.credentialFingerprint());
        }
    }

    private record RuntimeIdentity(
            CredentialKind kind,
            String projectId,
            long revision,
            String credentialFingerprint) {
    }
}
