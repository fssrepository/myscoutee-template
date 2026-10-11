package com.raxim.myscoutee.domain.platform.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.raxim.myscoutee.domain.platform.service.OperatorFirebaseCapabilityClient.FirebaseWebConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DefaultOperatorFirebaseCapabilityClient
        implements OperatorFirebaseCapabilityClient {
    private static final String TEST_UID =
            "myscoutee-operator-capability-test";
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private final FirebaseCapabilityOperations operations;
    private final FirebasePublicConfigurationOperations
            publicConfigurationOperations;

    static boolean hasAuthorizedDomain(
            JsonNode domains,
            String expectedDomain) {
        if (domains == null || !domains.isArray()) {
            return false;
        }
        String normalizedExpected = normalizeDomain(expectedDomain);
        if (!StringUtils.hasText(normalizedExpected)) {
            return false;
        }
        for (JsonNode domain : domains) {
            if (normalizeDomain(domain.asText(""))
                    .equals(normalizedExpected)) {
                return true;
            }
        }
        return false;
    }

    static boolean emailPasswordEnabled(JsonNode config) {
        return config != null
                && config.path("signIn")
                        .path("email")
                        .path("enabled")
                        .asBoolean(false);
    }

    static boolean enabledGoogleProvider(JsonNode provider) {
        if (provider == null) {
            return false;
        }
        return provider.path("enabled").asBoolean(false)
                && StringUtils.hasText(
                        provider.path("clientId").asText("").trim())
                && provider.path("name").asText("").trim().endsWith(
                        "/defaultSupportedIdpConfigs/google.com");
    }

    private static String normalizeDomain(String value) {
        String normalized = (value == null ? "" : value.trim())
                .toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - 1);
        }
        return normalized;
    }

    public DefaultOperatorFirebaseCapabilityClient() {
        this(
                new FirebaseCapabilityOperations() {
                    @Override
                    public void testAuthentication(FirebaseApp app)
                            throws FirebaseAuthException {
                        try {
                            FirebaseAuth.getInstance(app).getUser(TEST_UID);
                        } catch (FirebaseAuthException exception) {
                            if (exception.getAuthErrorCode()
                                    != AuthErrorCode.USER_NOT_FOUND) {
                                throw exception;
                            }
                            /*
                             * USER_NOT_FOUND is an authenticated response from
                             * the configured Firebase project and therefore
                             * proves the Admin provider round-trip without
                             * creating test data.
                             */
                        }
                    }

                    @Override
                    public String sendTestMessage(
                            FirebaseApp app,
                            String destinationToken,
                            String notificationTitle)
                            throws Exception {
                        Message message = Message.builder()
                                .setToken(destinationToken)
                                .setNotification(Notification.builder()
                                        .setTitle(notificationTitle)
                                        .setBody(
                                                "Operator messaging configuration is working.")
                                        .build())
                                .putData(
                                        "operator_capability_test",
                                        "true")
                                .build();
                        return FirebaseMessaging.getInstance(app)
                                .send(message);
                    }
                },
                new HttpFirebasePublicConfigurationOperations());
    }

    DefaultOperatorFirebaseCapabilityClient(
            FirebaseCapabilityOperations operations) {
        this(
                operations,
                (serviceAccount, projectId, configuration,
                        validateIdentityToolkit) -> {
                    /*
                     * Unit tests for isolated FirebaseApp lifecycle inject
                     * their own Admin operations and intentionally perform no
                     * network traffic.
                     */
                });
    }

    DefaultOperatorFirebaseCapabilityClient(
            FirebaseCapabilityOperations operations,
            FirebasePublicConfigurationOperations
                    publicConfigurationOperations) {
        this.operations = Objects.requireNonNull(operations);
        this.publicConfigurationOperations =
                Objects.requireNonNull(publicConfigurationOperations);
    }

    @Override
    public void testAuthentication(
            String projectId,
            String serviceAccountJson,
            FirebaseWebConfiguration webConfiguration) {
        FirebaseWebConfiguration configuration =
                requireWebConfiguration(webConfiguration, false);
        withIsolatedApp(
                projectId,
                serviceAccountJson,
                (app, serviceAccount, normalizedProjectId) -> {
                    publicConfigurationOperations.verify(
                            serviceAccount,
                            normalizedProjectId,
                            configuration,
                            true);
                    operations.testAuthentication(app);
                });
    }

    @Override
    public void sendTestMessage(
            String projectId,
            String serviceAccountJson,
            FirebaseWebConfiguration webConfiguration,
            String browserReadinessToken,
            String optionalDestinationToken,
            String notificationTitle) {
        FirebaseWebConfiguration configuration =
                requireWebConfiguration(webConfiguration, true);
        requireValidVapidPublicKey(configuration.vapidKey());
        String token = requireText(
                browserReadinessToken,
                "Firebase browser readiness token");
        String extraToken = optionalText(optionalDestinationToken);
        String title = requireText(
                notificationTitle,
                "Firebase notification title");
        withIsolatedApp(
                projectId,
                serviceAccountJson,
                (app, serviceAccount, normalizedProjectId) -> {
                    publicConfigurationOperations.verify(
                            serviceAccount,
                            normalizedProjectId,
                            configuration,
                            false);
                    requireProjectBoundMessage(
                            operations.sendTestMessage(
                                    app,
                                    token,
                                    title),
                            normalizedProjectId);
                    if (StringUtils.hasText(extraToken)
                            && !extraToken.equals(token)) {
                        requireProjectBoundMessage(
                                operations.sendTestMessage(
                                        app,
                                        extraToken,
                                        title),
                                normalizedProjectId);
                    }
                });
    }

    private void withIsolatedApp(
            String projectId,
            String serviceAccountJson,
            FirebaseOperation operation) {
        String normalizedProjectId = requireText(
                projectId,
                "Firebase project id");
        String credential = requireText(
                serviceAccountJson,
                "Firebase service-account credential");
        FirebaseApp app = null;
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(
                            credential.getBytes(StandardCharsets.UTF_8)));
            if (!(credentials
                    instanceof ServiceAccountCredentials serviceAccount)) {
                throw new IllegalArgumentException(
                        "Firebase credential must be a service-account JSON document.");
            }
            String credentialProjectId = serviceAccount.getProjectId();
            if (StringUtils.hasText(credentialProjectId)
                    && !normalizedProjectId.equals(
                            credentialProjectId.trim())) {
                throw new IllegalArgumentException(
                        "Firebase project id does not match the service-account credential.");
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(normalizedProjectId)
                    .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                    .setReadTimeout(READ_TIMEOUT_MILLIS)
                    .build();
            app = FirebaseApp.initializeApp(
                    options,
                    "operator-capability-" + UUID.randomUUID());
            operation.run(
                    app,
                    serviceAccount,
                    normalizedProjectId);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Firebase service-account credential is invalid.",
                    exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Firebase capability test failed.",
                    exception);
        } finally {
            if (app != null) {
                app.delete();
            }
        }
    }

    private FirebaseWebConfiguration requireWebConfiguration(
            FirebaseWebConfiguration configuration,
            boolean requireVapidKey) {
        if (configuration == null) {
            throw new IllegalArgumentException(
                    "Firebase browser configuration is required.");
        }
        String vapidKey = optionalText(configuration.vapidKey());
        if (requireVapidKey && !StringUtils.hasText(vapidKey)) {
            throw new IllegalArgumentException(
                    "Firebase VAPID public key is required.");
        }
        return new FirebaseWebConfiguration(
                requireText(
                        configuration.apiKey(),
                        "Firebase browser API key"),
                requireText(
                        configuration.authDomain(),
                        "Firebase auth domain"),
                optionalText(configuration.storageBucket()),
                requireText(
                        configuration.messagingSenderId(),
                        "Firebase messaging sender id"),
                requireText(
                        configuration.appId(),
                        "Firebase browser app id"),
                optionalText(configuration.measurementId()),
                vapidKey);
    }

    private void requireValidVapidPublicKey(String value) {
        byte[] encodedPoint;
        try {
            encodedPoint = Base64.getUrlDecoder().decode(
                    requireText(
                            value,
                            "Firebase VAPID public key"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Firebase VAPID public key is invalid.",
                    exception);
        }
        if (encodedPoint.length != 65 || encodedPoint[0] != 0x04) {
            throw new IllegalArgumentException(
                    "Firebase VAPID public key is invalid.");
        }
        BigInteger prime = new BigInteger(
                "FFFFFFFF00000001000000000000000000000000"
                        + "FFFFFFFFFFFFFFFFFFFFFFFF",
                16);
        BigInteger coefficientA = prime.subtract(BigInteger.valueOf(3L));
        BigInteger coefficientB = new BigInteger(
                "5AC635D8AA3A93E7B3EBBD55769886BC651D06B0"
                        + "CC53B0F63BCE3C3E27D2604B",
                16);
        BigInteger x = new BigInteger(
                1,
                Arrays.copyOfRange(encodedPoint, 1, 33));
        BigInteger y = new BigInteger(
                1,
                Arrays.copyOfRange(encodedPoint, 33, 65));
        if (x.compareTo(prime) >= 0
                || y.compareTo(prime) >= 0
                || !y.modPow(BigInteger.TWO, prime).equals(
                        x.modPow(BigInteger.valueOf(3L), prime)
                                .add(coefficientA.multiply(x))
                                .add(coefficientB)
                                .mod(prime))) {
            throw new IllegalArgumentException(
                    "Firebase VAPID public key is invalid.");
        }
    }

    private void requireProjectBoundMessage(
            String messageId,
            String projectId) {
        String expectedPrefix = "projects/"
                + projectId
                + "/messages/";
        if (!optionalText(messageId).startsWith(expectedPrefix)) {
            throw new IllegalStateException(
                    "Firebase messaging readiness verification failed.");
        }
    }

    private String requireText(String value, String fieldName) {
        String normalized = optionalText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(
                    fieldName + " is required.");
        }
        return normalized;
    }

    private String optionalText(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    private interface FirebaseOperation {
        void run(
                FirebaseApp app,
                ServiceAccountCredentials serviceAccount,
                String projectId) throws Exception;
    }

    interface FirebaseCapabilityOperations {
        void testAuthentication(FirebaseApp app) throws Exception;

        String sendTestMessage(
                FirebaseApp app,
                String destinationToken,
                String notificationTitle) throws Exception;
    }

    @FunctionalInterface
    interface FirebasePublicConfigurationOperations {
        void verify(
                ServiceAccountCredentials serviceAccount,
                String projectId,
                FirebaseWebConfiguration configuration,
                boolean validateIdentityToolkit) throws Exception;
    }

    private static final class
            HttpFirebasePublicConfigurationOperations
            implements FirebasePublicConfigurationOperations {
        private static final URI OAUTH_TOKEN_URI =
                URI.create("https://oauth2.googleapis.com/token");
        private static final String FIREBASE_MANAGEMENT_PREFIX =
                "https://firebase.googleapis.com/v1beta1/projects/";
        private static final String IDENTITY_TOOLKIT_PREFIX =
                "https://identitytoolkit.googleapis.com/v1/"
                        + "accounts:createAuthUri?key=";
        private static final String IDENTITY_TOOLKIT_ADMIN_PREFIX =
                "https://identitytoolkit.googleapis.com/v2/projects/";
        private static final String CLOUD_PLATFORM_SCOPE =
                "https://www.googleapis.com/auth/cloud-platform";
        private static final Duration CONNECT_TIMEOUT =
                Duration.ofMillis(CONNECT_TIMEOUT_MILLIS);
        private static final Duration REQUEST_TIMEOUT =
                Duration.ofMillis(READ_TIMEOUT_MILLIS);
        private static final int MAX_RESPONSE_BYTES = 65_536;
        private static final Base64.Encoder BASE64_URL =
                Base64.getUrlEncoder().withoutPadding();

        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        private HttpFirebasePublicConfigurationOperations() {
            this(
                    HttpClient.newBuilder()
                            .connectTimeout(CONNECT_TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build(),
                    new ObjectMapper());
        }

        private HttpFirebasePublicConfigurationOperations(
                HttpClient httpClient,
                ObjectMapper objectMapper) {
            this.httpClient = Objects.requireNonNull(httpClient);
            this.objectMapper = Objects.requireNonNull(objectMapper);
        }

        @Override
        public void verify(
                ServiceAccountCredentials serviceAccount,
                String projectId,
                FirebaseWebConfiguration configuration,
                boolean validateIdentityToolkit) throws Exception {
            String accessToken = exchangeAccessToken(serviceAccount);
            verifyManagementConfiguration(
                    accessToken,
                    projectId,
                    configuration);
            if (validateIdentityToolkit) {
                verifyAuthenticationConfiguration(
                        accessToken,
                        projectId,
                        configuration);
                verifyIdentityToolkit(configuration);
            }
        }

        private String exchangeAccessToken(
                ServiceAccountCredentials serviceAccount)
                throws Exception {
            long issuedAt = Instant.now().getEpochSecond();
            String assertion = signedJwt(
                    serviceAccount,
                    issuedAt);
            String form = "grant_type="
                    + encodeQuery(
                            "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    + "&assertion="
                    + encodeQuery(assertion);
            HttpRequest request = HttpRequest.newBuilder(OAUTH_TOKEN_URI)
                    .timeout(REQUEST_TIMEOUT)
                    .header(
                            "Content-Type",
                            "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            form,
                            StandardCharsets.UTF_8))
                    .build();
            BoundedResponse response = send(request);
            JsonNode body = successfulJson(
                    response,
                    "Firebase credential authorization");
            String accessToken = text(body, "access_token");
            if (!StringUtils.hasText(accessToken)) {
                throw new IllegalStateException(
                        "Firebase credential authorization returned an invalid response.");
            }
            return accessToken;
        }

        private String signedJwt(
                ServiceAccountCredentials serviceAccount,
                long issuedAt) throws Exception {
            Map<String, Object> header = Map.of(
                    "alg", "RS256",
                    "typ", "JWT",
                    "kid", Objects.toString(
                            serviceAccount.getPrivateKeyId(),
                            ""));
            Map<String, Object> claims = Map.of(
                    "iss", serviceAccount.getClientEmail(),
                    "scope", CLOUD_PLATFORM_SCOPE,
                    "aud", OAUTH_TOKEN_URI.toString(),
                    "iat", issuedAt,
                    "exp", issuedAt + 3_600L);
            String signingInput = encodeJson(header)
                    + "."
                    + encodeJson(claims);
            byte[] signature = serviceAccount.sign(
                    signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + BASE64_URL.encodeToString(signature);
        }

        private String encodeJson(Object value) throws Exception {
            return BASE64_URL.encodeToString(
                    objectMapper.writeValueAsBytes(value));
        }

        private void verifyManagementConfiguration(
                String accessToken,
                String projectId,
                FirebaseWebConfiguration expected) throws Exception {
            URI uri = URI.create(
                    FIREBASE_MANAGEMENT_PREFIX
                            + encodePathSegment(projectId)
                            + "/webApps/"
                            + encodePathSegment(expected.appId())
                            + "/config");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header(
                            "Authorization",
                            "Bearer " + accessToken)
                    .GET()
                    .build();
            JsonNode actual = successfulJson(
                    send(request),
                    "Firebase browser configuration lookup");
            requireExact(
                    text(actual, "projectId"),
                    projectId,
                    "project id");
            requireExact(
                    text(actual, "appId"),
                    expected.appId(),
                    "app id");
            requireExact(
                    text(actual, "apiKey"),
                    expected.apiKey(),
                    "browser API key");
            String storageBucket = text(actual, "storageBucket");
            if (StringUtils.hasText(expected.storageBucket())
                    && StringUtils.hasText(storageBucket)) {
                requireExact(
                        storageBucket,
                        expected.storageBucket(),
                        "storage bucket");
            }
            requireExact(
                    text(actual, "messagingSenderId"),
                    expected.messagingSenderId(),
                    "messaging sender id");
            requireExact(
                    text(actual, "projectNumber"),
                    expected.messagingSenderId(),
                    "project number");
            if (StringUtils.hasText(expected.measurementId())) {
                requireExact(
                        text(actual, "measurementId"),
                        expected.measurementId(),
                        "measurement id");
            }
        }

        private void verifyIdentityToolkit(
                FirebaseWebConfiguration configuration) throws Exception {
            URI uri = URI.create(
                    IDENTITY_TOOLKIT_PREFIX
                            + encodeQuery(configuration.apiKey()));
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "providerId",
                    "google.com",
                    "continueUri",
                    "https://" + configuration.authDomain()
                            + "/entry"));
            String origin = "https://" + configuration.authDomain();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Origin", origin)
                    .header("Referer", origin + "/")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            JsonNode result = successfulJson(
                    send(request),
                    "Firebase browser authentication lookup");
            requireExact(
                    text(result, "providerId"),
                    "google.com",
                    "authentication provider");
            if (!StringUtils.hasText(text(result, "authUri"))
                    || !StringUtils.hasText(
                            text(result, "sessionId"))) {
                throw new IllegalStateException(
                        "Firebase browser authentication lookup returned an invalid response.");
            }
        }

        private void verifyAuthenticationConfiguration(
                String accessToken,
                String projectId,
                FirebaseWebConfiguration configuration) throws Exception {
            URI uri = URI.create(
                    IDENTITY_TOOLKIT_ADMIN_PREFIX
                            + encodePathSegment(projectId)
                            + "/config?fields="
                            + encodeQuery(
                                    "name,authorizedDomains,"
                                            + "signIn(email(enabled,"
                                            + "passwordRequired))"));
            JsonNode config = successfulJson(
                    send(authorizedGet(uri, accessToken)),
                    "Firebase authentication configuration lookup");
            requireAuthorizedDomain(
                    config.path("authorizedDomains"),
                    configuration.authDomain());
            if (!emailPasswordEnabled(config)) {
                throw new IllegalStateException(
                        "Firebase email and password sign-in is not enabled.");
            }
            if (!enabledGoogleProvider(
                    loadDefaultProvider(
                            accessToken,
                            projectId,
                            "google.com"))) {
                throw new IllegalStateException(
                        "Firebase Google sign-in is not enabled.");
            }
        }

        private JsonNode loadDefaultProvider(
                String accessToken,
                String projectId,
                String providerId) throws Exception {
            URI uri = URI.create(
                    IDENTITY_TOOLKIT_ADMIN_PREFIX
                            + encodePathSegment(projectId)
                            + "/defaultSupportedIdpConfigs/"
                            + encodePathSegment(providerId)
                            + "?fields="
                            + encodeQuery("name,enabled,clientId"));
            BoundedResponse response = send(
                    authorizedGet(uri, accessToken));
            return successfulJson(
                    response,
                    "Firebase authentication provider lookup");
        }

        private HttpRequest authorizedGet(
                URI uri,
                String accessToken) {
            return HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header(
                            "Authorization",
                            "Bearer " + accessToken)
                    .GET()
                    .build();
        }

        private void requireAuthorizedDomain(
                JsonNode domains,
                String expectedDomain) {
            if (hasAuthorizedDomain(domains, expectedDomain)) {
                return;
            }
            throw new IllegalStateException(
                    "Firebase authentication domain is not authorized.");
        }

        private BoundedResponse send(HttpRequest request)
                throws IOException {
            try {
                HttpResponse<byte[]> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.limiting(
                                HttpResponse.BodyHandlers.ofByteArray(),
                                MAX_RESPONSE_BYTES));
                return new BoundedResponse(
                        response.statusCode(),
                        response.headers()
                                .firstValue("Content-Type")
                                .orElse(""),
                        response.body());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Firebase capability request was interrupted.",
                        exception);
            }
        }

        private JsonNode successfulJson(
                BoundedResponse response,
                String operation) throws Exception {
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        operation + " failed.");
            }
            if (!response.contentType()
                    .toLowerCase(Locale.ROOT)
                    .startsWith("application/json")) {
                throw new IllegalStateException(
                        operation + " returned an invalid response.");
            }
            JsonNode body = objectMapper.readTree(response.body());
            if (body == null || !body.isObject()) {
                throw new IllegalStateException(
                        operation + " returned an invalid response.");
            }
            return body;
        }

        private String text(JsonNode node, String fieldName) {
            String value = node.path(fieldName).asText("").trim();
            return StringUtils.hasText(value) ? value : "";
        }

        private void requireExact(
                String actual,
                String expected,
                String fieldName) {
            if (!Objects.equals(actual, expected)) {
                throw new IllegalStateException(
                        "Firebase browser " + fieldName
                                + " does not match the configured web app.");
            }
        }

        private void requireExactIgnoreCase(
                String actual,
                String expected,
                String fieldName) {
            if (!actual.equalsIgnoreCase(expected)) {
                throw new IllegalStateException(
                        "Firebase browser " + fieldName
                                + " does not match the configured web app.");
            }
        }

        private String encodePathSegment(String value) {
            return encodeQuery(value).replace("+", "%20");
        }

        private String encodeQuery(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }

        private record BoundedResponse(
                int statusCode,
                String contentType,
                byte[] body) {
        }
    }
}
