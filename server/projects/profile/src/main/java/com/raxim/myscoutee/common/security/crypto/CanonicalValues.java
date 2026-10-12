package com.raxim.myscoutee.common.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical signed-value encoding and validation shared by deployment releases and Registry. */
public final class CanonicalValues {
    private CanonicalValues() {}

    private static final Pattern HASH_PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");

    public static byte[] canonical(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            String value = requireCanonical(values[index], "canonical field " + index);
            canonical.append(value).append('\n');
        }
        return canonical.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] canonicalAllowEmpty(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            String value = values[index] == null ? "" : values[index];
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(
                        "canonical field " + index + " must not contain CR or LF.");
            }
            canonical.append(value).append('\n');
        }
        return canonical.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public static String publicKeyFingerprint(byte[] spkiDer) {
        return sha256(Objects.requireNonNull(spkiDer, "Public key DER must not be null."));
    }

    public static String standardBase64(byte[] value) {
        return Base64.getEncoder().encodeToString(Objects.requireNonNull(value, "Binary value must not be null."));
    }

    public static byte[] decodeStandardBase64(String value, String fieldName) {
        requireCanonical(value, fieldName);
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (!standardBase64(decoded).equals(value)) {
                throw new IllegalArgumentException(fieldName + " is not canonical padded base64.");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("canonical padded base64")) {
                throw exception;
            }
            throw new IllegalArgumentException(fieldName + " is not valid standard padded base64.", exception);
        }
    }

    public static String requireCanonical(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(fieldName + " must not contain CR or LF.");
        }
        return value;
    }

    public static String requireHash(String value, String fieldName) {
        requireCanonical(value, fieldName);
        if (!HASH_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a canonical SHA-256 value.");
        }
        return value;
    }

    public static Instant requireUtcTimestamp(String value, String fieldName) {
        requireCanonical(value, fieldName);
        if (!value.endsWith("Z")) {
            throw new IllegalArgumentException(fieldName + " must be an RFC 3339 UTC timestamp ending in Z.");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " must be an RFC 3339 UTC timestamp.", exception);
        }
    }

    public static String requiredLong(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null.");
        }
        return Long.toString(value);
    }

    public static String requirePattern(String value, String fieldName, Pattern pattern) {
        requireCanonical(value, fieldName);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " is malformed.");
        }
        return value;
    }
}
