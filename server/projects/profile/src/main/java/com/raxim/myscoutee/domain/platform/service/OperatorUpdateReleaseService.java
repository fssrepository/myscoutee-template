package com.raxim.myscoutee.domain.platform.service;
import com.raxim.myscoutee.domain.platform.api.ReleaseProtocol;
import com.raxim.myscoutee.common.security.crypto.CanonicalValues;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateArtifactDownloader;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateHttpClient;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.PublicKey;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.platform.config.OperatorUpdateProperties;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorAnnouncementDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateManifestDto;
import com.raxim.myscoutee.domain.platform.api.ReleaseProtocol.UpdateManifest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Public GitHub release discovery. No Registry identity, enrollment or service is used. */
@Service
public class OperatorUpdateReleaseService {
    private static final String ASSET_NAME = "myscoutee-update.json";
    private static final int MAX_METADATA_BYTES = 256 * 1024;
    private final OperatorUpdateProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public OperatorUpdateReleaseService(OperatorUpdateProperties properties, ObjectMapper mapper) {
        this(properties, mapper, OperatorUpdateHttpClient.create(properties));
    }

    OperatorUpdateReleaseService(OperatorUpdateProperties properties, ObjectMapper mapper, HttpClient client) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = client;
    }

    public record ReleasePage(String checkedAt, List<OperatorAnnouncementDto> items) {}

    private com.raxim.myscoutee.domain.notifications.api.RoleNotifications notifications;
    private com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner sessionRunner;

    @Autowired
    void setNotificationDelivery(com.raxim.myscoutee.domain.notifications.api.RoleNotifications notifications,
            com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner sessionRunner) {
        this.notifications = notifications;
        this.sessionRunner = sessionRunner;
    }

    public ReleasePage releases() {
        ReleasePage page = releases(null);
        notifyNewRelease(page);
        return page;
    }

    // Discovery is bounded and independent of browser activity. Never downloads or installs a package.
    @org.springframework.scheduling.annotation.Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT6H")
    public void checkReleaseNotifications() {
        if (notifications == null || !Files.isRegularFile(properties.getReleasePublicKeyPath()) || !knownInstalledVersion()) return;
        try {
            ReleasePage page = releases(null);
            sessionRunner.runAcrossConfiguredDatabases(() -> notifyNewRelease(page));
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Scheduled release notification check failed: {}", exception.getMessage());
        }
    }

    private boolean knownInstalledVersion() {
        try {
            ReleaseProtocol.requireSemanticVersion(properties.getSoftwareVersion(), "installed version");
            return true;
        } catch (IllegalArgumentException exception) { return false; }
    }

    private void notifyNewRelease(ReleasePage page) {
        if (notifications == null || !knownInstalledVersion()) return;
        for (OperatorAnnouncementDto item : page.items()) {
            String version = item.updateManifest().releaseVersion();
            if (ReleaseProtocol.compareSemanticVersions(ReleaseProtocol.requireSemanticVersion(version, "release version"),
                    ReleaseProtocol.requireSemanticVersion(properties.getSoftwareVersion(), "installed version")) > 0) {
                notifications.releaseAvailable(version);
            }
        }
    }

    public OperatorAnnouncementDto findVersion(String version) {
        ReleaseProtocol.requireSemanticVersion(version, "rollback version");
        return releases(version).items().stream().findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "The previous release is unavailable."));
    }

    private ReleasePage releases(String exactVersion) {
        String repository = properties.getGithubRepository();
        if (repository == null || !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The release repository is not configured.");
        }
        try {
            JsonNode releases = mapper.readTree(read(URI.create(
                    configuredOrigin(properties.getReleaseApiOrigin()) + "/repos/" + repository + "/releases?per_page=100"), 2 * 1024 * 1024));
            if (!releases.isArray()) {
                throw new IllegalArgumentException("Invalid release list.");
            }
            // Publication order can differ from version order when older releases are edited.
            // Select the highest version; postponing an update does not create an upgrade queue.
            List<JsonNode> candidates = new ArrayList<>();
            for (JsonNode release : releases) {
                if (exactVersion != null && !exactVersion.equals(releaseTagVersion(release))) continue;
                if (release.path("draft").asBoolean()
                        || (release.path("prerelease").asBoolean() && !properties.isIncludePrereleases())) {
                    continue;
                }
                for (JsonNode asset : release.path("assets")) {
                    if (asset.path("name").asText().endsWith(".deb")) {
                        releaseVersion(release); // Reject malformed version tags instead of hiding them.
                        candidates.add(release);
                        break;
                    }
                }
            }
            candidates.sort((left, right) -> ReleaseProtocol.compareSemanticVersions(
                    releaseVersion(right), releaseVersion(left)));
            for (JsonNode release : candidates) {
                JsonNode metadata = null;
                boolean hasPackage = false;
                for (JsonNode asset : release.path("assets")) {
                    hasPackage |= asset.path("name").asText().endsWith(".deb");
                    if (ASSET_NAME.equals(asset.path("name").asText())) {
                        if (metadata != null) throw new IllegalArgumentException("Duplicate update metadata.");
                        metadata = asset;
                    }
                }
                if (!hasPackage) continue;
                if (metadata == null) {
                    throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                            "The published release has no signed update metadata.");
                }
                String prefix = configuredOrigin(properties.getReleaseDownloadOrigin()) + "/" + repository + "/releases/download/";
                URI metadataUri = URI.create(metadata.path("browser_download_url").asText());
                if (!metadataUri.toString().startsWith(prefix)
                        || !metadataUri.getPath().endsWith("/" + ASSET_NAME)) {
                    throw new IllegalArgumentException("Unexpected update metadata origin.");
                }
                OperatorAnnouncementDto candidate = verify(read(metadataUri, MAX_METADATA_BYTES));
                if (!candidate.updateManifest().releaseVersion().equals(releaseTagVersion(release))) {
                    throw new IllegalArgumentException("Signed version does not match the selected release tag.");
                }
                boolean artifactMatches = false;
                for (JsonNode asset : release.path("assets")) {
                    artifactMatches |= candidate.updateManifest().artifactUrl().equals(asset.path("browser_download_url").asText())
                            && candidate.updateManifest().artifactSizeBytes() == asset.path("size").asLong();
                }
                if (!artifactMatches || !candidate.updateManifest().artifactUrl().startsWith(prefix)) {
                    throw new IllegalArgumentException("Signed package is not an asset of the selected release.");
                }
                return new ReleasePage(Instant.now().toString(), List.of(candidate));
            }
            return new ReleasePage(Instant.now().toString(), List.of());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Release discovery was interrupted.");
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not verify the published release.", exception);
        }
    }

    private static String releaseTagVersion(JsonNode release) {
        String tag = release.path("tag_name").asText();
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }

    private static ReleaseProtocol.SemanticVersion releaseVersion(JsonNode release) {
        return ReleaseProtocol.requireSemanticVersion(releaseTagVersion(release), "GitHub release tag");
    }

    public OperatorAnnouncementDto find(String id) {
        return releases().items().stream().filter(item -> item.announcementId().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The selected release has changed. Check for updates again."));
    }

    OperatorAnnouncementDto verify(byte[] envelopeBytes) throws Exception {
        JsonNode envelope = mapper.readTree(envelopeBytes);
        if (envelope.path("schemaVersion").asInt() != 1 || envelope.size() != 3) {
            throw new IllegalArgumentException("Unsupported signed release metadata.");
        }
        byte[] manifestBytes = Base64.getDecoder().decode(envelope.path("manifestBase64").asText());
        PublicKey key = releaseKey();
        if (!com.raxim.myscoutee.common.security.crypto.Ed25519Signatures.verifyBase64(key, signedMessage(manifestBytes), envelope.path("signature").asText())) {
            throw new IllegalArgumentException("Invalid release metadata signature.");
        }
        UpdateManifest manifest = mapper.readValue(manifestBytes, UpdateManifest.class);
        if (!"1".equals(manifest.manifestVersion()) || !"AVAILABLE".equals(manifest.status())
                || !Boolean.FALSE.equals(manifest.backupRequired())
                || manifest.expectedDowntimeSeconds() == null || manifest.expectedDowntimeSeconds() < 0
                || manifest.artifactSizeBytes() == null || manifest.artifactSizeBytes() < 1
                || manifest.artifactSizeBytes() > properties.getMaxArtifactBytes()
                || manifest.channel() == null || !manifest.channel().matches("[a-z][a-z0-9._-]{0,31}")) {
            throw new IllegalArgumentException("Invalid release metadata.");
        }
        ReleaseProtocol.requireSemanticVersion(manifest.releaseVersion(), "release version");
        var minimum = ReleaseProtocol.requireSemanticVersion(manifest.minimumCompatibleVersion(), "minimum version");
        var maximum = ReleaseProtocol.requireSemanticVersion(manifest.maximumCompatibleVersion(), "maximum version");
        if (ReleaseProtocol.compareSemanticVersions(minimum, maximum) > 0) {
            throw new IllegalArgumentException("Invalid compatibility range.");
        }
        CanonicalValues.requireUtcTimestamp(manifest.publishedAt(), "published_at");
        CanonicalValues.requireHash(manifest.artifactSha256(), "artifact hash");
        OperatorUpdateArtifactDownloader.requireHttpsArtifactUri(manifest.artifactUrl());
        if (!ReleaseProtocol.packageSigningKeyId(key.getEncoded()).equals(manifest.packageSigningKeyId())
                || !com.raxim.myscoutee.common.security.crypto.Ed25519Signatures.verifyBase64(key, ReleaseProtocol.releasePackage(manifest), manifest.packageSignature())) {
            throw new IllegalArgumentException("Invalid package signature.");
        }
        String hash = CanonicalValues.sha256(ReleaseProtocol.updateManifest(manifest));
        OperatorUpdateManifestDto dto = new OperatorUpdateManifestDto(
                manifest.manifestVersion(), manifest.releaseVersion(), manifest.channel(), manifest.publishedAt(),
                manifest.minimumCompatibleVersion(), manifest.maximumCompatibleVersion(), manifest.artifactUrl(),
                manifest.artifactSizeBytes(), manifest.artifactSha256(), manifest.packageSigningKeyId(),
                manifest.packageSignature(), manifest.releaseNotesUrl(), manifest.backupRequired(), manifest.expectedDowntimeSeconds(),
                manifest.status(), manifest.supersedesVersion(), manifest.supersededByVersion(), manifest.revocationReason(),
                true, true, true);
        // Retain the existing approval wire ID and durable job format. This is a content ID, not Registry enrollment.
        return new OperatorAnnouncementDto(1, "ann_" + hash.substring(7, 39), "UPDATE", "NOTICE",
                manifest.publishedAt(), null, null, null, List.of(), List.of(), dto, hash, hash);
    }

    static byte[] signedMessage(byte[] manifest) {
        byte[] prefix = "myscoutee-update-catalog-v1\n".getBytes(StandardCharsets.UTF_8);
        byte[] result = java.util.Arrays.copyOf(prefix, prefix.length + manifest.length);
        System.arraycopy(manifest, 0, result, prefix.length, manifest.length);
        return result;
    }

    private PublicKey releaseKey() throws Exception {
        var path = properties.getReleasePublicKeyPath();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 4096) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "The trusted release key is not configured.");
        }
        return com.raxim.myscoutee.common.security.crypto.Ed25519Signatures.parsePublicKeyPemOrDer(Files.readAllBytes(path), "release public key");
    }

    private static URI configuredOrigin(String value) {
        URI origin = URI.create(value);
        if (!"https".equals(origin.getScheme()) || origin.getHost() == null
                || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                || (origin.getPort() != -1 && (origin.getPort() < 1 || origin.getPort() > 65535))
                || !(origin.getRawPath().isEmpty() || origin.getRawPath().equals("/"))) {
            throw new IllegalArgumentException("Release origin must be an HTTPS origin without a path or credentials.");
        }
        return URI.create(origin.toString().replaceAll("/$", ""));
    }

    private static boolean sameOrigin(URI left, URI right) {
        return Objects.equals(left.getScheme(), right.getScheme())
                && Objects.equals(left.getHost(), right.getHost())
                && (left.getPort() == -1 ? 443 : left.getPort()) == (right.getPort() == -1 ? 443 : right.getPort());
    }

    private byte[] read(URI uri, int maximumBytes) throws Exception {
        URI apiOrigin = configuredOrigin(properties.getReleaseApiOrigin());
        URI downloadOrigin = configuredOrigin(properties.getReleaseDownloadOrigin());
        List<URI> visited = new ArrayList<>();
        for (int redirects = 0; redirects <= 5; redirects++) {
            String host = uri.getHost();
            boolean allowed = sameOrigin(uri, apiOrigin) || sameOrigin(uri, downloadOrigin);
            // GitHub asset redirects are needed only for the default GitHub source.
            allowed |= downloadOrigin.equals(URI.create("https://github.com"))
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (Objects.equals(host, "release-assets.githubusercontent.com")
                        || Objects.equals(host, "objects.githubusercontent.com"));
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getFragment() != null
                    || !allowed || visited.contains(uri)) {
                throw new IllegalArgumentException("Invalid release metadata endpoint.");
            }
            visited.add(uri);
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/octet-stream, application/vnd.github+json")
                    .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (List.of(301, 302, 303, 307, 308).contains(response.statusCode())) {
                    uri = uri.resolve(response.headers().firstValue("Location").orElseThrow());
                    continue;
                }
                if (response.statusCode() != 200) throw new java.io.IOException("Release metadata request failed.");
                byte[] bytes = input.readNBytes(maximumBytes + 1);
                if (bytes.length > maximumBytes) throw new java.io.IOException("Release metadata exceeds its size limit.");
                return bytes;
            }
        }
        throw new java.io.IOException("Release metadata redirect limit exceeded.");
    }
}
