package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.mapper.OperatorConfigurationMapper;
import com.raxim.myscoutee.domain.platform.service.DeploymentConfigurationCrypto;
import com.raxim.myscoutee.domain.platform.service.DeploymentFirebaseRuntime;
import com.raxim.myscoutee.domain.platform.service.OperatorFirebaseCapabilityClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.BrandingUpdateDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.ConfigurationTestKind;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.ConfigurationTestRequestDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.ConfigurationTestResultDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.DeploymentBrandingDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.DeploymentPublicConfigurationDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.DeploymentSocialLinkDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.FirebasePublicConfigurationDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.FirebaseUpdateDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.IntegrationUpdateDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.OperatorConfigurationDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.OperatorConfigurationUpdateDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.PrivacyContactUpdateDto;
import com.raxim.myscoutee.domain.platform.repository.DeploymentConfigurationRepository;
import com.raxim.myscoutee.domain.platform.service.AppSettingsService;
import com.raxim.myscoutee.domain.platform.service.OperatorFirebaseCapabilityClient.FirebaseWebConfiguration;

@Service
public class OperatorConfigurationService implements com.raxim.myscoutee.domain.platform.api.DeploymentConfigurationManagement {
    public static final String FIREBASE_AUTHENTICATION_PURPOSE =
            "firebase-authentication-credential";
    public static final String FIREBASE_MESSAGING_PURPOSE =
            "firebase-messaging-credential";

    private static final String DEFAULT_LOGO_WEBP = "assets/logo/heart.webp";
    private static final String DEFAULT_LOGO_PNG = "assets/logo/heart.png";
    private static final int PRODUCT_NAME_MAX = 80;
    private static final int LOGO_URL_MAX = 2048;
    private static final int PUBLIC_BASE_URL_MAX = 2048;
    private static final int FIREBASE_PROJECT_ID_MAX = 160;
    private static final int FIREBASE_API_KEY_MAX = 256;
    private static final int FIREBASE_AUTH_DOMAIN_MAX = 253;
    private static final int FIREBASE_STORAGE_BUCKET_MAX = 512;
    private static final int FIREBASE_MESSAGING_SENDER_ID_MAX = 64;
    private static final int FIREBASE_APP_ID_MAX = 256;
    private static final int FIREBASE_MEASUREMENT_ID_MAX = 64;
    private static final int FIREBASE_VAPID_KEY_MAX = 512;
    private static final int FIREBASE_CREDENTIAL_MAX = 65536;
    private static final int ADMIN_EMAIL_MAX = 254;
    private static final int DATA_CONTROLLER_NAME_MAX = 160;
    private static final int PRIVACY_CONTACT_EMAIL_MAX = 254;
    private static final int ADMIN_EMAIL_COUNT_MAX = 32;
    private static final int SOCIAL_LINK_COUNT_MAX = 12;
    private static final Pattern ADMIN_EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+$");
    private static final Pattern PRIVACY_CONTACT_EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern SOCIAL_PROVIDER_PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9._-]*$");

    private final DeploymentConfigurationRepository repository;
    private final AppSettingsService appSettingsService;
    private final OperatorConfigurationMapper mapper;
    private final DeploymentConfigurationCrypto crypto;
    private final OperatorFirebaseCapabilityClient firebaseCapabilityClient;
    private final DeploymentFirebaseRuntime firebaseRuntime;
    private final Clock clock;
    private final EnumMap<MongoSessionMode, Object> modeLocks =
            new EnumMap<>(MongoSessionMode.class);

    @Autowired
    public OperatorConfigurationService(
            DeploymentConfigurationRepository repository,
            AppSettingsService appSettingsService,
            OperatorConfigurationMapper mapper,
            DeploymentConfigurationCrypto crypto,
            OperatorFirebaseCapabilityClient firebaseCapabilityClient,
            DeploymentFirebaseRuntime firebaseRuntime) {
        this(
                repository,
                appSettingsService,
                mapper,
                crypto,
                Clock.systemUTC(),
                firebaseCapabilityClient,
                firebaseRuntime);
    }

    OperatorConfigurationService(
            DeploymentConfigurationRepository repository,
            AppSettingsService appSettingsService,
            OperatorConfigurationMapper mapper,
            DeploymentConfigurationCrypto crypto,
            Clock clock) {
        this(
                repository,
                appSettingsService,
                mapper,
                crypto,
                clock,
                unavailableFirebaseCapabilityClient(),
                null);
    }

    OperatorConfigurationService(
            DeploymentConfigurationRepository repository,
            AppSettingsService appSettingsService,
            OperatorConfigurationMapper mapper,
            DeploymentConfigurationCrypto crypto,
            Clock clock,
            OperatorFirebaseCapabilityClient firebaseCapabilityClient) {
        this(
                repository,
                appSettingsService,
                mapper,
                crypto,
                clock,
                firebaseCapabilityClient,
                null);
    }

    OperatorConfigurationService(
            DeploymentConfigurationRepository repository,
            AppSettingsService appSettingsService,
            OperatorConfigurationMapper mapper,
            DeploymentConfigurationCrypto crypto,
            Clock clock,
            OperatorFirebaseCapabilityClient firebaseCapabilityClient,
            DeploymentFirebaseRuntime firebaseRuntime) {
        this.repository = Objects.requireNonNull(repository);
        this.appSettingsService = Objects.requireNonNull(appSettingsService);
        this.mapper = Objects.requireNonNull(mapper);
        this.crypto = Objects.requireNonNull(crypto);
        this.clock = Objects.requireNonNull(clock);
        this.firebaseCapabilityClient =
                Objects.requireNonNull(firebaseCapabilityClient);
        this.firebaseRuntime = firebaseRuntime;
        for (MongoSessionMode mode : MongoSessionMode.values()) {
            modeLocks.put(mode, new Object());
        }
    }

    public DeploymentBrandingDto branding() {
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        synchronized (modeLocks.get(mode)) {
            return mapper.toBranding(loadPersisted());
        }
    }

    public DeploymentPublicConfigurationDto publicConfiguration() {
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        synchronized (modeLocks.get(mode)) {
            return mapper.toPublicConfiguration(loadPersisted());
        }
    }

    public FirebasePublicConfigurationDto publicFirebaseConfiguration() {
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        synchronized (modeLocks.get(mode)) {
            if (mode == MongoSessionMode.DEMO) {
                return null;
            }
            DeploymentConfiguration configuration = loadPersisted();
            DeploymentConfiguration.Firebase firebase =
                    configuration.getFirebase();
            if (!firebase.isActive()) {
                return null;
            }
            try {
                crypto.decrypt(
                        FIREBASE_AUTHENTICATION_PURPOSE,
                        firebase.getAuthenticationCredentialEnvelope());
                crypto.decrypt(
                        FIREBASE_MESSAGING_PURPOSE,
                        firebase.getMessagingCredentialEnvelope());
            } catch (RuntimeException exception) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Firebase client configuration is temporarily unavailable.",
                        exception);
            }
            if (!completePublicFirebaseConfiguration(firebase)) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Active Firebase client configuration is incomplete.");
            }
            return mapper.toPublicFirebaseConfiguration(configuration);
        }
    }

    public OperatorConfigurationDto configuration() {
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        synchronized (modeLocks.get(mode)) {
            DeploymentConfiguration configuration = loadPersisted();
            return mapper.toOperatorConfiguration(
                    configuration,
                    appSettingsService.adminEmails(),
                    crypto.unavailableReason(configuration));
        }
    }

    @CacheEvict(value = "deployment_integration", allEntries = true)
    public OperatorConfigurationDto update(OperatorConfigurationUpdateDto request) {
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        synchronized (modeLocks.get(mode)) {
            if (request == null || request.branding() == null) {
                throw badRequest("Branding configuration is required.");
            }
            List<String> adminEmails = normalizeAdminEmails(
                    request.adminEmails());
            List<DeploymentSocialLinkDto> socialLinks =
                    normalizeSocialLinks(request.socialLinks());
            DeploymentConfiguration configuration = loadPersisted();
            String cryptoGap = crypto.unavailableReason(configuration);
            if (StringUtils.hasText(cryptoGap)) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        cryptoGap);
            }
            applyBranding(configuration, request.branding());
            applyPrivacyContact(configuration, request.privacyContact());
            applySocialLinks(configuration, socialLinks);
            applyIntegration(configuration, request.integration());
            applyFirebase(configuration, request.firebase());
            configuration.setSchemaVersion(DeploymentConfiguration.SCHEMA_VERSION);
            configuration.setUpdatedAt(clock.instant().toString());
            DeploymentConfiguration saved = saveUpdate(configuration);
            refreshFirebaseRuntime();
            List<String> savedAdminEmails =
                    appSettingsService.updateAdminEmails(adminEmails);
            return mapper.toOperatorConfiguration(
                    saved,
                    savedAdminEmails,
                    null);
        }
    }

    public OperatorConfigurationDto activateFirebase() {
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        synchronized (modeLocks.get(mode)) {
            if (mode == MongoSessionMode.DEMO) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Live Firebase activation is unavailable in Explore/demo mode.");
            }
            DeploymentConfiguration configuration = loadPersisted();
            String cryptoGap = crypto.unavailableReason(configuration);
            if (StringUtils.hasText(cryptoGap)) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        cryptoGap);
            }
            DeploymentConfiguration.Firebase firebase =
                    configuration.getFirebase();
            long revision = firebaseRevision(firebase);
            if (!completePublicFirebaseConfiguration(firebase)
                    || firebase.getAuthenticationCredentialEnvelope() == null
                    || firebase.getMessagingCredentialEnvelope() == null
                    || !Long.valueOf(revision).equals(
                            firebase.getAuthenticationTestedRevision())
                    || !Long.valueOf(revision).equals(
                            firebase.getMessagingTestedRevision())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Save the complete Firebase client configuration and "
                                + "successfully test authentication and messaging "
                                + "before activation.");
            }
            firebase.setActive(true);
            firebase.setActivatedAt(clock.instant().toString());
            configuration.setUpdatedAt(clock.instant().toString());
            DeploymentConfiguration saved = saveUpdate(configuration);
            refreshFirebaseRuntime();
            return mapper.toOperatorConfiguration(
                    saved,
                    appSettingsService.adminEmails(),
                    null);
        }
    }

    public ConfigurationTestResultDto test(ConfigurationTestRequestDto request) {
        if (request == null || request.kind() == null) {
            throw badRequest("Configuration test kind is required.");
        }
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        synchronized (modeLocks.get(mode)) {
            if (mode == MongoSessionMode.DEMO) {
                return firebaseTestResult(
                        request.kind(),
                        false,
                        "Live Firebase capability tests are unavailable in Explore/demo mode.",
                        clock.instant());
            }
            DeploymentConfiguration configuration = loadPersisted();
            DeploymentConfiguration.Firebase firebase = configuration.getFirebase();
            Instant testedAt = clock.instant();
            ConfigurationTestResultDto result = switch (request.kind()) {
                case FIREBASE_AUTHENTICATION -> testFirebaseAuthentication(
                        firebase,
                        testedAt);
                case FIREBASE_MESSAGING -> testFirebaseMessaging(
                        firebase,
                        request.browserReadinessToken(),
                        request.browserConfigurationRevision(),
                        request.browserAppId(),
                        request.destinationToken(),
                        configuration.getBranding().getProductName(),
                        testedAt);
            };
            DeploymentConfiguration authoritative =
                    recordFirebaseTest(
                            configuration,
                            request,
                            result,
                            testedAt);
            return new ConfigurationTestResultDto(
                    result.kind(),
                    result.success(),
                    result.message(),
                    result.testedAt(),
                    mapper.toFirebaseConfiguration(authoritative));
        }
    }

    private ConfigurationTestResultDto testFirebaseAuthentication(
            DeploymentConfiguration.Firebase firebase,
            Instant testedAt) {
        if (!StringUtils.hasText(firebase.getProjectId())
                || firebase.getAuthenticationCredentialEnvelope() == null) {
            return firebaseTestResult(
                    ConfigurationTestKind.FIREBASE_AUTHENTICATION,
                    false,
                    "Firebase authentication test unavailable: projectId and "
                            + "authentication credential must be configured.",
                    testedAt);
        }
        try {
            String credential = crypto.decrypt(
                    FIREBASE_AUTHENTICATION_PURPOSE,
                    firebase.getAuthenticationCredentialEnvelope());
            firebaseCapabilityClient.testAuthentication(
                    firebase.getProjectId().trim(),
                    credential,
                    firebaseWebConfiguration(firebase));
            return firebaseTestResult(
                    ConfigurationTestKind.FIREBASE_AUTHENTICATION,
                    true,
                    "Firebase authentication and browser configuration tests succeeded.",
                    testedAt);
        } catch (RuntimeException exception) {
            return firebaseTestResult(
                    ConfigurationTestKind.FIREBASE_AUTHENTICATION,
                    false,
                    "Firebase authentication test failed. Check the configured "
                            + "web app, browser API key, project, and "
                            + "service-account permissions.",
                    testedAt);
        }
    }

    private ConfigurationTestResultDto testFirebaseMessaging(
            DeploymentConfiguration.Firebase firebase,
            String browserReadinessToken,
            Long browserConfigurationRevision,
            String browserAppId,
            String optionalDestinationToken,
            String productName,
            Instant testedAt) {
        if (!StringUtils.hasText(firebase.getProjectId())
                || firebase.getMessagingCredentialEnvelope() == null) {
            return firebaseTestResult(
                    ConfigurationTestKind.FIREBASE_MESSAGING,
                    false,
                    "Firebase messaging test unavailable: projectId and messaging "
                            + "credential must be configured.",
                    testedAt);
        }
        DeploymentConfiguration.FirebasePublicClient publicClient =
                firebase.getPublicClient();
        if (!StringUtils.hasText(browserReadinessToken)
                || browserConfigurationRevision == null
                || browserConfigurationRevision
                        != firebaseRevision(firebase)
                || publicClient == null
                || !StringUtils.hasText(browserAppId)
                || !browserAppId.trim().equals(
                        publicClient.getAppId())) {
            return firebaseTestResult(
                    ConfigurationTestKind.FIREBASE_MESSAGING,
                    false,
                    "Firebase messaging test failed. Browser readiness proof "
                            + "for the current saved configuration is required.",
                    testedAt);
        }
        try {
            String credential = crypto.decrypt(
                    FIREBASE_MESSAGING_PURPOSE,
                    firebase.getMessagingCredentialEnvelope());
            firebaseCapabilityClient.sendTestMessage(
                    firebase.getProjectId().trim(),
                    credential,
                    firebaseWebConfiguration(firebase),
                    browserReadinessToken.trim(),
                    optionalDestinationToken == null
                            ? null
                            : optionalDestinationToken.trim(),
                    productName.trim());
            return firebaseTestResult(
                    ConfigurationTestKind.FIREBASE_MESSAGING,
                    true,
                    "Firebase browser messaging readiness test succeeded.",
                    testedAt);
        } catch (RuntimeException exception) {
            return firebaseTestResult(
                    ConfigurationTestKind.FIREBASE_MESSAGING,
                    false,
                    "Firebase messaging test failed. Check the configured web "
                            + "app, VAPID public key, project, browser support, "
                            + "and service-account permissions.",
                    testedAt);
        }
    }

    private ConfigurationTestResultDto firebaseTestResult(
            ConfigurationTestKind kind,
            boolean success,
            String message,
            Instant testedAt) {
        return new ConfigurationTestResultDto(
                kind,
                success,
                message,
                testedAt.toString(),
                null);
    }

    private DeploymentConfiguration recordFirebaseTest(
            DeploymentConfiguration configuration,
            ConfigurationTestRequestDto request,
            ConfigurationTestResultDto result,
            Instant testedAt) {
        DeploymentConfiguration.Firebase firebase =
                configuration.getFirebase();
        long revision = firebaseRevision(firebase);
        boolean attempted;
        if (request.kind()
                == ConfigurationTestKind.FIREBASE_AUTHENTICATION) {
            attempted = StringUtils.hasText(firebase.getProjectId())
                    && firebase.getAuthenticationCredentialEnvelope() != null;
            if (!attempted) {
                return configuration;
            }
            firebase.setAuthenticationTestedRevision(
                    result.success() ? revision : null);
            firebase.setAuthenticationTestedAt(
                    result.success() ? testedAt.toString() : null);
        } else {
            attempted = StringUtils.hasText(firebase.getProjectId())
                    && firebase.getMessagingCredentialEnvelope() != null;
            if (!attempted) {
                return configuration;
            }
            firebase.setMessagingTestedRevision(
                    result.success() ? revision : null);
            firebase.setMessagingTestedAt(
                    result.success() ? testedAt.toString() : null);
        }
        if (!result.success()) {
            firebase.setActive(false);
            firebase.setActivatedAt(null);
        }
        configuration.setUpdatedAt(testedAt.toString());
        DeploymentConfiguration saved = saveUpdate(configuration);
        refreshFirebaseRuntime();
        return saved;
    }

    private FirebaseWebConfiguration firebaseWebConfiguration(
            DeploymentConfiguration.Firebase firebase) {
        DeploymentConfiguration.FirebasePublicClient publicClient =
                firebase.getPublicClient();
        return new FirebaseWebConfiguration(
                publicClient == null ? null : publicClient.getApiKey(),
                publicClient == null ? null : publicClient.getAuthDomain(),
                publicClient == null ? null : publicClient.getStorageBucket(),
                publicClient == null
                        ? null
                        : publicClient.getMessagingSenderId(),
                publicClient == null ? null : publicClient.getAppId(),
                publicClient == null ? null : publicClient.getMeasurementId(),
                publicClient == null ? null : publicClient.getVapidKey());
    }

    private void refreshFirebaseRuntime() {
        if (firebaseRuntime != null) {
            firebaseRuntime.refreshAfterConfigurationChange();
        }
    }

    private static OperatorFirebaseCapabilityClient
            unavailableFirebaseCapabilityClient() {
        return new OperatorFirebaseCapabilityClient() {
            @Override
            public void testAuthentication(
                    String projectId,
                    String serviceAccountJson,
                    FirebaseWebConfiguration webConfiguration) {
                throw new IllegalStateException(
                        "Firebase capability client is unavailable.");
            }

            @Override
            public void sendTestMessage(
                    String projectId,
                    String serviceAccountJson,
                    FirebaseWebConfiguration webConfiguration,
                    String browserReadinessToken,
                    String optionalDestinationToken,
                    String notificationTitle) {
                throw new IllegalStateException(
                        "Firebase capability client is unavailable.");
            }
        };
    }

    private DeploymentConfiguration loadPersisted() {
        return repository.findById(DeploymentConfiguration.SINGLETON_ID)
                .map(this::requireCompleteSeed)
                .orElseThrow(() -> unavailable(
                        "Deployment configuration seed is missing."));
    }

    private DeploymentConfiguration requireCompleteSeed(
            DeploymentConfiguration configuration) {
        if (!DeploymentConfiguration.SINGLETON_ID.equals(configuration.getId())
                || configuration.getVersion() == null
                || configuration.getSchemaVersion() == null
                || configuration.getSchemaVersion()
                        != DeploymentConfiguration.SCHEMA_VERSION
                || configuration.getBranding() == null
                || configuration.getSocialLinks() == null
                || configuration.getPrivacyContact() == null
                || configuration.getIntegration() == null
                || configuration.getFirebase() == null) {
            throw unavailable(
                    "Deployment configuration seed is incomplete or unsupported.");
        }
        DeploymentConfiguration.Branding branding = configuration.getBranding();
        if (!StringUtils.hasText(branding.getProductName())
                || !StringUtils.hasText(branding.getHomeLabel())
                || branding.getLogoUrl() == null
                || branding.getRevision() == null
                || branding.getRevision() < 0L) {
            throw unavailable(
                    "Deployment branding seed is incomplete.");
        }
        if (!validLogoCharacterIndex(
                branding.getProductName().trim(),
                branding.getLogoCharacterIndex())) {
            throw unavailable(
                    "Deployment branding seed has an invalid logo character index.");
        }
        if (configuration.getFirebase().getProjectId() == null) {
            throw unavailable(
                    "Deployment Firebase seed is incomplete.");
        }
        DeploymentConfiguration.Firebase firebase =
                configuration.getFirebase();
        if (firebase.getRevision() != null
                && firebase.getRevision() < 0L) {
            throw unavailable(
                    "Deployment Firebase revision is invalid.");
        }
        long firebaseRevision = firebaseRevision(firebase);
        if (firebase.isActive()
                && (!completePublicFirebaseConfiguration(firebase)
                        || firebase.getAuthenticationCredentialEnvelope() == null
                        || firebase.getMessagingCredentialEnvelope() == null
                        || !Long.valueOf(firebaseRevision).equals(
                                firebase.getAuthenticationTestedRevision())
                        || !Long.valueOf(firebaseRevision).equals(
                                firebase.getMessagingTestedRevision()))) {
            throw unavailable(
                    "Activated deployment Firebase configuration is incomplete or unverified.");
        }
        try {
            mapper.toSocialLinks(configuration);
        } catch (IllegalStateException exception) {
            throw unavailable(
                    "Deployment social-link seed is invalid.",
                    exception);
        }
        requireValidPersistedPrivacyContact(configuration.getPrivacyContact());
        return configuration;
    }

    private DeploymentConfiguration saveUpdate(
            DeploymentConfiguration configuration) {
        try {
            return repository.save(configuration);
        } catch (OptimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Deployment configuration changed concurrently; reload it and retry.",
                    exception);
        }
    }

    private void applyBranding(
            DeploymentConfiguration configuration,
            BrandingUpdateDto request) {
        String productName = requiredPublicText(
                request.productName(),
                "Product name",
                PRODUCT_NAME_MAX);
        Integer logoCharacterIndex = request.logoCharacterIndex() == null
                ? 0
                : request.logoCharacterIndex();
        if (!validLogoCharacterIndex(productName, logoCharacterIndex)) {
            throw badRequest(
                    "Logo character index must be -1 or identify a Unicode "
                            + "character in the product name.");
        }
        String logoUrl = validateLogoUrl(request.logoUrl());

        DeploymentConfiguration.Branding branding = configuration.getBranding();
        branding.setProductName(productName);
        branding.setLogoCharacterIndex(logoCharacterIndex);
        branding.setLogoUrl(logoUrl);
        long currentRevision = branding.getRevision();
        if (currentRevision == Long.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Branding revision cannot be advanced.");
        }
        branding.setRevision(currentRevision + 1L);
    }

    private boolean validLogoCharacterIndex(
            String productName,
            Integer logoCharacterIndex) {
        if (logoCharacterIndex == null) {
            return true;
        }
        int characterCount = productName.codePointCount(
                0,
                productName.length());
        return logoCharacterIndex >= -1
                && logoCharacterIndex < characterCount;
    }

    private void applyIntegration(
            DeploymentConfiguration configuration,
            IntegrationUpdateDto request) {
        if (request == null) {
            return;
        }
        configuration.getIntegration().setPublicBaseUrl(
                publicBaseUrl(
                        request.publicBaseUrl(),
                        "Integration public base URL"));
    }

    private String publicBaseUrl(String value, String fieldName) {
        String normalized = optionalTrimmed(
                value,
                fieldName,
                PUBLIC_BASE_URL_MAX);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            boolean secure = "https".equalsIgnoreCase(scheme);
            boolean loopback = "http".equalsIgnoreCase(scheme)
                    && ("localhost".equalsIgnoreCase(uri.getHost())
                            || "127.0.0.1".equals(uri.getHost())
                            || "::1".equals(uri.getHost()));
            if ((!secure && !loopback)
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw badRequest(
                        fieldName + " must be an absolute HTTPS URL "
                                + "(HTTP is accepted only for loopback development).");
            }
            String path = uri.getPath();
            String withoutTrailingSlash = normalized;
            while (withoutTrailingSlash.endsWith("/")) {
                withoutTrailingSlash = withoutTrailingSlash.substring(
                        0,
                        withoutTrailingSlash.length() - 1);
            }
            if (path != null && path.contains("..")) {
                throw badRequest(
                        fieldName + " contains an invalid path.");
            }
            return withoutTrailingSlash;
        } catch (URISyntaxException exception) {
            throw badRequest(fieldName + " is invalid.");
        }
    }

    private void applySocialLinks(
            DeploymentConfiguration configuration,
            List<DeploymentSocialLinkDto> links) {
        List<DeploymentConfiguration.SocialLink> stored =
                new ArrayList<>(links.size());
        for (DeploymentSocialLinkDto link : links) {
            DeploymentConfiguration.SocialLink item =
                    new DeploymentConfiguration.SocialLink();
            item.setProvider(link.provider());
            item.setLabel(link.label());
            item.setUrl(link.url());
            item.setIcon(link.icon());
            item.setHandle(link.handle());
            stored.add(item);
        }
        configuration.setSocialLinks(List.copyOf(stored));
    }

    private void applyPrivacyContact(
            DeploymentConfiguration configuration,
            PrivacyContactUpdateDto request) {
        if (request == null) {
            return;
        }
        String dataControllerName = privacyText(
                request.dataControllerName(),
                "Data controller name",
                DATA_CONTROLLER_NAME_MAX);
        String privacyContactEmail = privacyText(
                request.privacyContactEmail(),
                "Privacy contact email",
                PRIVACY_CONTACT_EMAIL_MAX)
                .toLowerCase(Locale.ROOT);
        boolean hasDataControllerName =
                StringUtils.hasText(dataControllerName);
        boolean hasPrivacyContactEmail =
                StringUtils.hasText(privacyContactEmail);
        if (hasDataControllerName != hasPrivacyContactEmail) {
            throw badRequest(
                    "Data controller name and privacy contact email must "
                            + "either both be configured or both be blank.");
        }
        if (hasPrivacyContactEmail
                && !PRIVACY_CONTACT_EMAIL_PATTERN
                        .matcher(privacyContactEmail)
                        .matches()) {
            throw badRequest("Privacy contact email is invalid.");
        }
        DeploymentConfiguration.PrivacyContact stored =
                configuration.getPrivacyContact();
        stored.setDataControllerName(dataControllerName);
        stored.setPrivacyContactEmail(privacyContactEmail);
    }

    private void applyFirebase(
            DeploymentConfiguration configuration,
            FirebaseUpdateDto request) {
        if (request == null) {
            return;
        }
        DeploymentConfiguration.Firebase firebase = configuration.getFirebase();
        DeploymentConfiguration.FirebasePublicClient publicClient =
                firebase.getPublicClient();
        if (publicClient == null) {
            publicClient =
                    new DeploymentConfiguration.FirebasePublicClient();
            firebase.setPublicClient(publicClient);
        }
        boolean changed = false;
        if (request.projectId() != null) {
            String projectId = firebasePublicText(
                    request.projectId(),
                    "Firebase project id",
                    FIREBASE_PROJECT_ID_MAX);
            changed |= !Objects.equals(firebase.getProjectId(), projectId);
            firebase.setProjectId(projectId);
        }
        if (request.apiKey() != null) {
            String apiKey = firebasePublicText(
                    request.apiKey(),
                    "Firebase browser API key",
                    FIREBASE_API_KEY_MAX);
            changed |= !Objects.equals(publicClient.getApiKey(), apiKey);
            publicClient.setApiKey(apiKey);
        }
        if (request.authDomain() != null) {
            String authDomain = firebaseAuthDomain(request.authDomain());
            changed |= !Objects.equals(
                    publicClient.getAuthDomain(),
                    authDomain);
            publicClient.setAuthDomain(authDomain);
        }
        if (request.storageBucket() != null) {
            String storageBucket = firebasePublicText(
                    request.storageBucket(),
                    "Firebase storage bucket",
                    FIREBASE_STORAGE_BUCKET_MAX);
            changed |= !Objects.equals(
                    publicClient.getStorageBucket(),
                    storageBucket);
            publicClient.setStorageBucket(storageBucket);
        }
        if (request.messagingSenderId() != null) {
            String messagingSenderId = firebasePublicText(
                    request.messagingSenderId(),
                    "Firebase messaging sender id",
                    FIREBASE_MESSAGING_SENDER_ID_MAX);
            changed |= !Objects.equals(
                    publicClient.getMessagingSenderId(),
                    messagingSenderId);
            publicClient.setMessagingSenderId(messagingSenderId);
        }
        if (request.appId() != null) {
            String appId = firebasePublicText(
                    request.appId(),
                    "Firebase browser app id",
                    FIREBASE_APP_ID_MAX);
            changed |= !Objects.equals(publicClient.getAppId(), appId);
            publicClient.setAppId(appId);
        }
        if (request.measurementId() != null) {
            String measurementId = firebasePublicText(
                    request.measurementId(),
                    "Firebase measurement id",
                    FIREBASE_MEASUREMENT_ID_MAX);
            changed |= !Objects.equals(
                    publicClient.getMeasurementId(),
                    measurementId);
            publicClient.setMeasurementId(measurementId);
        }
        if (request.vapidKey() != null) {
            String vapidKey = firebasePublicText(
                    request.vapidKey(),
                    "Firebase VAPID public key",
                    FIREBASE_VAPID_KEY_MAX);
            changed |= !Objects.equals(
                    publicClient.getVapidKey(),
                    vapidKey);
            publicClient.setVapidKey(vapidKey);
        }
        String authenticationCredential = optionalSecret(
                request.authenticationCredential(),
                "Firebase authentication credential",
                FIREBASE_CREDENTIAL_MAX);
        String messagingCredential = optionalSecret(
                request.messagingCredential(),
                "Firebase messaging credential",
                FIREBASE_CREDENTIAL_MAX);
        if (StringUtils.hasText(authenticationCredential)) {
            firebase.setAuthenticationCredentialEnvelope(crypto.encrypt(
                    FIREBASE_AUTHENTICATION_PURPOSE,
                    authenticationCredential));
            changed = true;
        }
        if (StringUtils.hasText(messagingCredential)) {
            firebase.setMessagingCredentialEnvelope(crypto.encrypt(
                    FIREBASE_MESSAGING_PURPOSE,
                    messagingCredential));
            changed = true;
        }
        if (changed) {
            long currentRevision = firebaseRevision(firebase);
            if (currentRevision == Long.MAX_VALUE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Firebase configuration revision cannot be advanced.");
            }
            firebase.setRevision(currentRevision + 1L);
            firebase.setActive(false);
            firebase.setAuthenticationTestedRevision(null);
            firebase.setAuthenticationTestedAt(null);
            firebase.setMessagingTestedRevision(null);
            firebase.setMessagingTestedAt(null);
            firebase.setActivatedAt(null);
        }
    }

    private String firebasePublicText(
            String value,
            String fieldName,
            int maximumLength) {
        String normalized = optionalTrimmed(
                value,
                fieldName,
                maximumLength);
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw badRequest(fieldName + " contains an invalid character.");
        }
        return normalized;
    }

    private String firebaseAuthDomain(String value) {
        String normalized = firebasePublicText(
                value,
                "Firebase auth domain",
                FIREBASE_AUTH_DOMAIN_MAX);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        try {
            URI uri = new URI("https://" + normalized);
            if (!normalized.equals(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1
                    || StringUtils.hasText(uri.getPath())
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw badRequest(
                        "Firebase auth domain must be a hostname without a scheme or path.");
            }
            return normalized.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException exception) {
            throw badRequest(
                    "Firebase auth domain must be a valid hostname.");
        }
    }

    private boolean completePublicFirebaseConfiguration(
            DeploymentConfiguration.Firebase firebase) {
        DeploymentConfiguration.FirebasePublicClient publicClient =
                firebase == null ? null : firebase.getPublicClient();
        return firebase != null
                && publicClient != null
                && StringUtils.hasText(firebase.getProjectId())
                && StringUtils.hasText(publicClient.getApiKey())
                && StringUtils.hasText(publicClient.getAuthDomain())
                && StringUtils.hasText(publicClient.getMessagingSenderId())
                && StringUtils.hasText(publicClient.getAppId())
                && StringUtils.hasText(publicClient.getVapidKey());
    }

    private long firebaseRevision(
            DeploymentConfiguration.Firebase firebase) {
        return firebase == null || firebase.getRevision() == null
                ? 0L
                : Math.max(0L, firebase.getRevision());
    }

    private String validateLogoUrl(String value) {
        String logoUrl = optionalTrimmed(value, "Logo URL", LOGO_URL_MAX);
        if (!StringUtils.hasText(logoUrl)
                || DEFAULT_LOGO_WEBP.equals(logoUrl)
                || DEFAULT_LOGO_PNG.equals(logoUrl)) {
            return logoUrl;
        }
        try {
            URI uri = URI.create(logoUrl);
            boolean managedPath = "/media/public".equals(uri.getPath());
            boolean supportedLocation = !uri.isAbsolute()
                    && logoUrl.startsWith("/media/public?");
            if (!managedPath
                    || !supportedLocation
                    || uri.getFragment() != null
                    || !hasMediaKey(uri.getRawQuery())) {
                throw badRequest(
                        "Logo URL must be a persisted /media/public?key= reference "
                                + "returned by the media upload flow.");
            }
            return logoUrl;
        } catch (IllegalArgumentException exception) {
            throw badRequest(
                    "Logo URL must be a valid persisted media reference.");
        }
    }

    private boolean hasMediaKey(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return false;
        }
        for (String parameter : rawQuery.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator > 0
                    && "key".equals(parameter.substring(0, separator))
                    && separator < parameter.length() - 1) {
                return true;
            }
        }
        return false;
    }

    private String requiredTrimmed(String value, String fieldName, int maxLength) {
        String normalized = optionalTrimmed(value, fieldName, maxLength);
        if (!StringUtils.hasText(normalized)) {
            throw badRequest(fieldName + " is required.");
        }
        return normalized;
    }

    private String requiredPublicText(
            String value,
            String fieldName,
            int maximumLength) {
        String normalized = requiredTrimmed(
                value,
                fieldName,
                maximumLength);
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw badRequest(fieldName + " contains an invalid character.");
        }
        return normalized;
    }

    private String privacyText(
            String value,
            String fieldName,
            int maximumLength) {
        String normalized = optionalTrimmed(
                value,
                fieldName,
                maximumLength);
        if (normalized.chars().anyMatch(Character::isISOControl)) {
            throw badRequest(fieldName + " contains an invalid character.");
        }
        return normalized;
    }

    private void requireValidPersistedPrivacyContact(
            DeploymentConfiguration.PrivacyContact privacyContact) {
        String storedName = privacyContact.getDataControllerName();
        String storedEmail = privacyContact.getPrivacyContactEmail();
        String dataControllerName =
                storedName == null ? "" : storedName.trim();
        String privacyContactEmail =
                storedEmail == null ? "" : storedEmail.trim();
        boolean hasDataControllerName =
                StringUtils.hasText(dataControllerName);
        boolean hasPrivacyContactEmail =
                StringUtils.hasText(privacyContactEmail);
        boolean invalid = hasDataControllerName != hasPrivacyContactEmail
                || dataControllerName.length() > DATA_CONTROLLER_NAME_MAX
                || privacyContactEmail.length()
                        > PRIVACY_CONTACT_EMAIL_MAX
                || dataControllerName.chars()
                        .anyMatch(Character::isISOControl)
                || privacyContactEmail.chars()
                        .anyMatch(Character::isISOControl)
                || (hasPrivacyContactEmail
                        && (!privacyContactEmail.equals(
                                privacyContactEmail.toLowerCase(Locale.ROOT))
                                || !PRIVACY_CONTACT_EMAIL_PATTERN
                                        .matcher(privacyContactEmail)
                                        .matches()))
                || (storedName != null
                        && !storedName.equals(dataControllerName))
                || (storedEmail != null
                        && !storedEmail.equals(privacyContactEmail));
        if (invalid) {
            throw unavailable(
                    "Deployment privacy-contact seed is invalid.");
        }
    }

    private String optionalTrimmed(String value, String fieldName, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw badRequest(fieldName + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private String optionalSecret(String value, String fieldName, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw badRequest(fieldName + " is too large.");
        }
        return normalized;
    }

    private List<String> normalizeAdminEmails(List<String> values) {
        if (values == null) {
            throw badRequest("Admin email list is required.");
        }
        if (values.size() > ADMIN_EMAIL_COUNT_MAX) {
            throw badRequest(
                    "No more than " + ADMIN_EMAIL_COUNT_MAX
                            + " admin emails may be configured.");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String email = value == null
                    ? ""
                    : value.trim().toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(email)
                    || email.length() > ADMIN_EMAIL_MAX
                    || !ADMIN_EMAIL_PATTERN.matcher(email).matches()) {
                throw badRequest("A configured admin email is invalid.");
            }
            normalized.add(email);
        }
        return List.copyOf(normalized);
    }

    private List<DeploymentSocialLinkDto> normalizeSocialLinks(
            List<DeploymentSocialLinkDto> values) {
        if (values == null) {
            throw badRequest("Social-link list is required.");
        }
        if (values.size() > SOCIAL_LINK_COUNT_MAX) {
            throw badRequest(
                    "No more than " + SOCIAL_LINK_COUNT_MAX
                            + " social links may be configured.");
        }
        Map<String, DeploymentSocialLinkDto> byProvider =
                new LinkedHashMap<>();
        Map<String, DeploymentSocialLinkDto> byUrl =
                new LinkedHashMap<>();
        for (DeploymentSocialLinkDto value : values) {
            if (value == null) {
                throw badRequest("A configured social link is invalid.");
            }
            String provider = requiredSocialText(
                    value.provider(),
                    "Social provider",
                    32)
                    .toLowerCase(Locale.ROOT);
            if (!SOCIAL_PROVIDER_PATTERN.matcher(provider).matches()) {
                throw badRequest(
                        "Social provider must be a lowercase identifier.");
            }
            DeploymentSocialLinkDto normalized =
                    new DeploymentSocialLinkDto(
                            provider,
                            requiredSocialText(
                                    value.label(),
                                    "Social label",
                                    80),
                            validateSocialUrl(value.url()),
                            optionalSocialText(
                                    value.icon(),
                                    "Social icon",
                                    64),
                            optionalSocialText(
                                    value.handle(),
                                    "Social handle",
                                    80));
            String urlKey = socialUrlKey(normalized.url());
            DeploymentSocialLinkDto providerDuplicate =
                    byProvider.get(provider);
            DeploymentSocialLinkDto urlDuplicate = byUrl.get(urlKey);
            if (providerDuplicate != null || urlDuplicate != null) {
                DeploymentSocialLinkDto duplicate =
                        providerDuplicate != null
                                ? providerDuplicate
                                : urlDuplicate;
                if (duplicate.equals(normalized)) {
                    continue;
                }
                throw badRequest(
                        "Social links contain a duplicate provider or URL.");
            }
            byProvider.put(provider, normalized);
            byUrl.put(urlKey, normalized);
        }
        return List.copyOf(byProvider.values());
    }

    private String requiredSocialText(
            String value,
            String fieldName,
            int maximumLength) {
        String normalized = optionalSocialText(
                value,
                fieldName,
                maximumLength);
        if (!StringUtils.hasText(normalized)) {
            throw badRequest(fieldName + " is required.");
        }
        return normalized;
    }

    private String optionalSocialText(
            String value,
            String fieldName,
            int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw badRequest(fieldName + " is invalid.");
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String validateSocialUrl(String value) {
        String normalized = requiredSocialText(
                value,
                "Social URL",
                LOGO_URL_MAX);
        try {
            URI uri = new URI(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null) {
                throw badRequest(
                        "Social URL must be an HTTPS URL without credentials.");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw badRequest("Social URL must be a valid HTTPS URL.");
        }
    }

    private String socialUrlKey(String value) {
        URI uri = URI.create(value);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort() == 443 ? -1 : uri.getPort();
        return "https://"
                + host
                + (port < 0 ? "" : ":" + port)
                + (uri.getRawPath() == null ? "" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                + (uri.getRawFragment() == null
                        ? ""
                        : "#" + uri.getRawFragment());
    }

    private String appendReason(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second;
        }
        return first.trim() + " " + second;
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage().trim()
                : exception.getClass().getSimpleName();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                message);
    }

    private ResponseStatusException unavailable(
            String message,
            RuntimeException cause) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                message,
                cause);
    }
}
