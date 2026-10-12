package com.raxim.myscoutee.domain.platform.api;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonProperty;
import static com.raxim.myscoutee.common.security.crypto.CanonicalValues.*;

/** Signed release manifest and version contract. No Registry enrollment, client or state dependency. */
public final class ReleaseProtocol {
    private ReleaseProtocol() {}

    public static final String UPDATE_MANIFEST_VERSION = "1";

    private static final Pattern ANNOUNCEMENT_ID_PATTERN = Pattern.compile("ann_[0-9a-f]{32}");

    private static final Pattern PACKAGE_SIGNING_KEY_ID_PATTERN =
            Pattern.compile("pkey_[0-9a-f]{32}");

    public static byte[] updateManifest(UpdateManifest manifest) {
        Objects.requireNonNull(manifest, "Update manifest must not be null.");
        return canonicalAllowEmpty(
                "myscoutee-registry-update-manifest-v1",
                manifest.manifestVersion(),
                manifest.releaseVersion(),
                manifest.channel(),
                manifest.publishedAt(),
                manifest.minimumCompatibleVersion(),
                manifest.maximumCompatibleVersion(),
                manifest.artifactUrl(),
                requiredLong(manifest.artifactSizeBytes(), "artifact_size_bytes"),
                manifest.artifactSha256(),
                manifest.packageSigningKeyId(),
                manifest.packageSignature(),
                manifest.releaseNotesUrl(),
                Boolean.toString(Boolean.TRUE.equals(manifest.backupRequired())),
                requiredLong(manifest.expectedDowntimeSeconds(), "expected_downtime_seconds"),
                manifest.status(),
                manifest.supersedesVersion(),
                manifest.supersededByVersion(),
                manifest.revocationReason());
    }

    public static byte[] releasePackage(UpdateManifest manifest) {
        Objects.requireNonNull(manifest, "Update manifest must not be null.");
        return canonicalAllowEmpty(
                "myscoutee-release-package-v1",
                manifest.releaseVersion(),
                manifest.channel(),
                requiredLong(manifest.artifactSizeBytes(), "artifact_size_bytes"),
                manifest.artifactSha256());
    }

    public static String requireAnnouncementId(String value) {
        return requirePattern(value, "announcement_id", ANNOUNCEMENT_ID_PATTERN);
    }

    public static String requirePackageSigningKeyId(String value) {
        return requirePattern(
                value,
                "package_signing_key_id",
                PACKAGE_SIGNING_KEY_ID_PATTERN);
    }

    public static String packageSigningKeyId(byte[] publicKeyDer) {
        String fingerprint = publicKeyFingerprint(publicKeyDer);
        return "pkey_" + fingerprint.substring(
                "sha256:".length(),
                "sha256:".length() + 32);
    }

    public static SemanticVersion requireSemanticVersion(
            String value,
            String fieldName) {
        String supplied = Objects.requireNonNull(
                value,
                fieldName + " must not be null.");
        if (supplied.length() < 5
                || supplied.length() > 128
                || supplied.indexOf('\n') >= 0
                || supplied.indexOf('\r') >= 0) {
            throw invalidSemanticVersion(fieldName);
        }
        String[] buildParts = supplied.split("\\+", -1);
        if (buildParts.length > 2
                || (buildParts.length == 2
                        && !validSemanticIdentifiers(buildParts[1], false))) {
            throw invalidSemanticVersion(fieldName);
        }
        String[] prereleaseParts = buildParts[0].split("-", 2);
        if (prereleaseParts.length == 2
                && !validSemanticIdentifiers(prereleaseParts[1], true)) {
            throw invalidSemanticVersion(fieldName);
        }
        String[] core = prereleaseParts[0].split("\\.", -1);
        if (core.length != 3) {
            throw invalidSemanticVersion(fieldName);
        }
        BigInteger[] numbers = new BigInteger[3];
        for (int index = 0; index < core.length; index++) {
            String part = core[index];
            if (part.isEmpty()
                    || (part.length() > 1 && part.charAt(0) == '0')
                    || !allAsciiDigits(part)) {
                throw invalidSemanticVersion(fieldName);
            }
            numbers[index] = new BigInteger(part);
            if (numbers[index].bitLength() > 64) {
                throw invalidSemanticVersion(fieldName);
            }
        }
        return new SemanticVersion(
                numbers[0],
                numbers[1],
                numbers[2],
                prereleaseParts.length == 2
                        ? List.of(prereleaseParts[1].split("\\.", -1))
                        : List.of());
    }

    public static int compareSemanticVersions(
            SemanticVersion left,
            SemanticVersion right) {
        Objects.requireNonNull(left, "Left semantic version must not be null.");
        Objects.requireNonNull(right, "Right semantic version must not be null.");
        int comparison = left.major().compareTo(right.major());
        if (comparison == 0) {
            comparison = left.minor().compareTo(right.minor());
        }
        if (comparison == 0) {
            comparison = left.patch().compareTo(right.patch());
        }
        if (comparison != 0) {
            return comparison;
        }
        if (left.prerelease().isEmpty() && right.prerelease().isEmpty()) {
            return 0;
        }
        if (left.prerelease().isEmpty()) {
            return 1;
        }
        if (right.prerelease().isEmpty()) {
            return -1;
        }
        int length = Math.min(
                left.prerelease().size(),
                right.prerelease().size());
        for (int index = 0; index < length; index++) {
            String leftPart = left.prerelease().get(index);
            String rightPart = right.prerelease().get(index);
            if (leftPart.equals(rightPart)) {
                continue;
            }
            boolean leftNumeric = allAsciiDigits(leftPart);
            boolean rightNumeric = allAsciiDigits(rightPart);
            if (leftNumeric && rightNumeric) {
                int numeric = new BigInteger(leftPart)
                        .compareTo(new BigInteger(rightPart));
                if (numeric != 0) {
                    return numeric;
                }
            } else if (leftNumeric) {
                return -1;
            } else if (rightNumeric) {
                return 1;
            } else {
                return leftPart.compareTo(rightPart);
            }
        }
        return Integer.compare(
                left.prerelease().size(),
                right.prerelease().size());
    }

    private static boolean validSemanticIdentifiers(
            String value,
            boolean rejectNumericLeadingZero) {
        if (value.isEmpty()) {
            return false;
        }
        for (String identifier : value.split("\\.", -1)) {
            if (identifier.isEmpty()) {
                return false;
            }
            boolean numeric = true;
            for (int index = 0; index < identifier.length(); index++) {
                char character = identifier.charAt(index);
                if (!((character >= '0' && character <= '9')
                        || (character >= 'A' && character <= 'Z')
                        || (character >= 'a' && character <= 'z')
                        || character == '-')) {
                    return false;
                }
                if (character < '0' || character > '9') {
                    numeric = false;
                }
            }
            if (rejectNumericLeadingZero
                    && numeric
                    && identifier.length() > 1
                    && identifier.charAt(0) == '0') {
                return false;
            }
        }
        return true;
    }

    private static boolean allAsciiDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException invalidSemanticVersion(
            String fieldName) {
        return new IllegalArgumentException(
                fieldName + " must be a canonical semantic version.");
    }

    public record UpdateManifest(
            @JsonProperty("manifest_version") String manifestVersion,
            @JsonProperty("release_version") String releaseVersion,
            String channel,
            @JsonProperty("published_at") String publishedAt,
            @JsonProperty("minimum_compatible_version") String minimumCompatibleVersion,
            @JsonProperty("maximum_compatible_version") String maximumCompatibleVersion,
            @JsonProperty("artifact_url") String artifactUrl,
            @JsonProperty("artifact_size_bytes") Long artifactSizeBytes,
            @JsonProperty("artifact_sha256") String artifactSha256,
            @JsonProperty("package_signing_key_id") String packageSigningKeyId,
            @JsonProperty("package_signature") String packageSignature,
            @JsonProperty("release_notes_url") String releaseNotesUrl,
            @JsonProperty("backup_required") Boolean backupRequired,
            @JsonProperty("expected_downtime_seconds") Long expectedDowntimeSeconds,
            String status,
            @JsonProperty("supersedes_version") String supersedesVersion,
            @JsonProperty("superseded_by_version") String supersededByVersion,
            @JsonProperty("revocation_reason") String revocationReason) {
    }

    public record SemanticVersion(
            BigInteger major,
            BigInteger minor,
            BigInteger patch,
            List<String> prerelease) {
        public SemanticVersion {
            prerelease = prerelease == null
                    ? List.of()
                    : List.copyOf(prerelease);
        }
    }
}
