package com.raxim.myscoutee.domain.platform.security.bootstrap;
import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapTokenInvalidException;
import com.raxim.myscoutee.domain.platform.api.OperatorBootstrapRequestSupport;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.accounts.api.BootstrapAccounts;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;

@Service
public class OperatorBootstrapAuthenticationService {
    public static final Duration TOKEN_TTL = Duration.ofHours(8);
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(1);
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final int PASSWORD_MAX_LENGTH = 512;
    static final int TOKEN_MAX_LENGTH = 2048;

    private static final String TOKEN_VERSION = "1";
    private static final int TOKEN_NONCE_BYTES = 16;
    private static final int HMAC_BYTES = 32;
    private static final long CLOCK_SKEW_SECONDS = 60L;
    private static final Pattern SHA256_HEX =
            Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern BOOTSTRAP_EMAIL =
            Pattern.compile(
                    "^operator-[0-9a-f]{24}@deployment\\.invalid$");
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER =
            Base64.getUrlDecoder();
    private static final byte[] TOKEN_KEY_CONTEXT =
            "myscoutee/operator-bootstrap/token-key/v1"
                    .getBytes(StandardCharsets.US_ASCII);

    private final RuntimeConfiguration config;
    private final BootstrapAccounts firebaseService;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Object failureLock = new Object();

    private String failureSubject = "";
    private Instant failureWindowStarted = Instant.EPOCH;
    private int failedAttempts;

    @Autowired
    public OperatorBootstrapAuthenticationService(
            RuntimeConfiguration config,
            BootstrapAccounts firebaseService) {
        this(
                config,
                firebaseService,
                Clock.systemUTC(),
                new SecureRandom());
    }

    OperatorBootstrapAuthenticationService(
            RuntimeConfiguration config,
            BootstrapAccounts firebaseService,
            Clock clock,
            SecureRandom secureRandom) {
        this.config = config;
        this.firebaseService = firebaseService;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public LoginResult login(String email, String password) {
        BootstrapConfiguration bootstrap = configuration()
                .orElseThrow(this::notApplicable);
        String normalizedEmail = normalizeEmail(email);
        if (!bootstrap.email().equals(normalizedEmail)) {
            throw notApplicable();
        }
        if (!StringUtils.hasText(password)
                || password.length() > PASSWORD_MAX_LENGTH) {
            throw unauthorized();
        }
        verifyPassword(bootstrap, password);

        UserDetails principal =
                firebaseService.loadOrCreateBootstrapOperator(
                        bootstrap.email());
        if (!hasOperatorAuthority(principal)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Operator bootstrap identity could not be persisted.");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(TOKEN_TTL);
        return new LoginResult(
                OperatorBootstrapRequestSupport.AUTHORIZATION_SCHEME,
                issueToken(
                        bootstrap,
                        issuedAt,
                        expiresAt),
                bootstrap.email(),
                expiresAt);
    }

    public boolean isConfigured() {
        return configuration().isPresent();
    }

    public UserDetails authenticateToken(String token) {
        BootstrapConfiguration bootstrap = configuration()
                .orElseThrow(
                        OperatorBootstrapTokenInvalidException::new);
        String email = verifyToken(bootstrap, token);
        UserDetails principal =
                firebaseService.loadStoredBootstrapOperator(email);
        if (!hasOperatorAuthority(principal)) {
            throw new OperatorBootstrapTokenInvalidException();
        }
        return principal;
    }

    private Optional<BootstrapConfiguration> configuration() {
        String operatorSubject = config.getOperatorUser() == null
                ? ""
                : config.getOperatorUser();
        String bootstrapEmail =
                config.getOperatorBootstrapEmail() == null
                        ? ""
                        : config.getOperatorBootstrapEmail();
        String digestHex = config.getOperatorBootstrapPasswordSha256();
        digestHex = digestHex == null ? "" : digestHex.trim();
        if (!operatorSubject.equals(bootstrapEmail)
                || bootstrapEmail.length() > 254
                || !BOOTSTRAP_EMAIL.matcher(bootstrapEmail).matches()
                || !SHA256_HEX.matcher(digestHex).matches()) {
            return Optional.empty();
        }
        byte[] digest = HexFormat.of().parseHex(digestHex);
        byte[] tokenKey = hmac(digest, TOKEN_KEY_CONTEXT);
        return Optional.of(new BootstrapConfiguration(
                bootstrapEmail,
                digestHex,
                digest,
                tokenKey));
    }

    private void verifyPassword(
            BootstrapConfiguration bootstrap,
            String password) {
        synchronized (failureLock) {
            Instant now = clock.instant();
            resetFailureWindowWhenRequired(bootstrap, now);
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Operator bootstrap login is temporarily throttled.");
            }
            byte[] candidate = sha256(
                    password.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(
                    bootstrap.passwordDigest(),
                    candidate)) {
                failedAttempts++;
                throw unauthorized();
            }
            failedAttempts = 0;
            failureWindowStarted = now;
        }
    }

    private void resetFailureWindowWhenRequired(
            BootstrapConfiguration bootstrap,
            Instant now) {
        String subject = bootstrap.email()
                + ":"
                + bootstrap.passwordDigestHex();
        if (!subject.equals(failureSubject)
                || now.isBefore(failureWindowStarted)
                || !now.isBefore(
                        failureWindowStarted.plus(FAILURE_WINDOW))) {
            failureSubject = subject;
            failureWindowStarted = now;
            failedAttempts = 0;
        }
    }

    private String issueToken(
            BootstrapConfiguration bootstrap,
            Instant issuedAt,
            Instant expiresAt) {
        byte[] nonce = new byte[TOKEN_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        String payload = TOKEN_VERSION
                + "."
                + issuedAt.getEpochSecond()
                + "."
                + expiresAt.getEpochSecond()
                + "."
                + encode(bootstrap.email().getBytes(StandardCharsets.UTF_8))
                + "."
                + encode(nonce);
        return payload
                + "."
                + encode(hmac(
                        bootstrap.tokenKey(),
                        payload.getBytes(StandardCharsets.US_ASCII)));
    }

    private String verifyToken(
            BootstrapConfiguration bootstrap,
            String token) {
        if (!StringUtils.hasText(token)
                || token.length() > TOKEN_MAX_LENGTH) {
            throw new OperatorBootstrapTokenInvalidException();
        }
        String normalized = token.trim();
        int signatureSeparator = normalized.lastIndexOf('.');
        if (signatureSeparator <= 0
                || signatureSeparator == normalized.length() - 1) {
            throw new OperatorBootstrapTokenInvalidException();
        }
        String payload = normalized.substring(0, signatureSeparator);
        byte[] suppliedSignature = decode(
                normalized.substring(signatureSeparator + 1));
        byte[] expectedSignature = hmac(
                bootstrap.tokenKey(),
                payload.getBytes(StandardCharsets.US_ASCII));
        if (suppliedSignature.length != HMAC_BYTES
                || !MessageDigest.isEqual(
                        expectedSignature,
                        suppliedSignature)) {
            throw new OperatorBootstrapTokenInvalidException();
        }

        String[] fields = payload.split("\\.", -1);
        if (fields.length != 5
                || !TOKEN_VERSION.equals(fields[0])) {
            throw new OperatorBootstrapTokenInvalidException();
        }
        long issuedAt = parseEpoch(fields[1]);
        long expiresAt = parseEpoch(fields[2]);
        if (expiresAt != safeExpiry(issuedAt)) {
            throw new OperatorBootstrapTokenInvalidException();
        }
        String email = normalizeEmail(new String(
                decode(fields[3]),
                StandardCharsets.UTF_8));
        if (!bootstrap.email().equals(email)
                || decode(fields[4]).length != TOKEN_NONCE_BYTES) {
            throw new OperatorBootstrapTokenInvalidException();
        }

        long now = clock.instant().getEpochSecond();
        if (issuedAt > now + CLOCK_SKEW_SECONDS
                || now >= expiresAt) {
            throw new OperatorBootstrapTokenInvalidException();
        }
        return email;
    }

    private long safeExpiry(long issuedAt) {
        try {
            return Math.addExact(issuedAt, TOKEN_TTL.toSeconds());
        } catch (ArithmeticException exception) {
            throw new OperatorBootstrapTokenInvalidException();
        }
    }

    private long parseEpoch(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new OperatorBootstrapTokenInvalidException();
        }
    }

    private byte[] decode(String value) {
        try {
            return BASE64_URL_DECODER.decode(value);
        } catch (IllegalArgumentException exception) {
            throw new OperatorBootstrapTokenInvalidException();
        }
    }

    private String encode(byte[] value) {
        return BASE64_URL_ENCODER.encodeToString(value);
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception);
        }
    }

    private byte[] hmac(byte[] key, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "HMAC-SHA256 is unavailable.",
                    exception);
        }
    }

    private boolean hasOperatorAuthority(UserDetails principal) {
        return principal != null
                && principal.getAuthorities().stream()
                        .anyMatch(authority -> "O".equals(
                                authority.getAuthority()));
    }

    private String normalizeEmail(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException notApplicable() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Operator bootstrap login is not configured.");
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Operator bootstrap credentials are invalid.");
    }

    public record LoginResult(
            String tokenType,
            String accessToken,
            String email,
            Instant expiresAt) {
    }

    private record BootstrapConfiguration(
            String email,
            String passwordDigestHex,
            byte[] passwordDigest,
            byte[] tokenKey) {
    }
}
