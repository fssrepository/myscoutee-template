package com.raxim.myscoutee.domain.platform.service;
import com.raxim.myscoutee.domain.platform.api.ReleaseProtocol;
import com.raxim.myscoutee.common.security.crypto.CanonicalValues;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateArtifactDownloader;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateReleaseService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.config.OperatorUpdateProperties;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorAnnouncementDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateManifestDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateApprovalDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorRollbackDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateEventDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateEventPageDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateJobDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateOverviewDto;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.ObjectMapper;

/**
 * Stages one release-key-signed update for the local privileged host helper.
 *
 * <p>The browser supplies only an announcement ID. The manifest URL, digest,
 * size, version and detached package signature are taken from a freshly
 * verified GitHub release metadata. Java downloads and stages the package but never
 * invokes a package manager or accepts a command. Java phases are stored below
 * {@code staging/<jobId>}; after the atomic request handoff, status readers
 * prefer the root worker's separate {@code jobs/<jobId>} directory.</p>
 */
@Service
public class OperatorUpdateService {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(OperatorUpdateService.class);
    private static final Pattern JOB_ID =
            Pattern.compile("^upd_[0-9a-f]{32}$");
    private static final Pattern RELEASE_VERSION =
            Pattern.compile("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(-qa\\.[1-9][0-9]*)?$");
    private static final Pattern CHANNEL =
            Pattern.compile("^[a-z][a-z0-9._-]{0,31}$");
    private static final Set<String> TERMINAL_PHASES =
            Set.of("COMPLETED", "FAILED", "REJECTED", "RECOVERY_REQUIRED");
    private static final int MAX_EVENT_FILE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_EVENT_LINES = 10_000;
    private static final int MAX_DISCOVERY_ENTRIES = 10_000;

    private final OperatorUpdateReleaseService releaseService;
    private final OperatorUpdateArtifactDownloader downloader;
    private final OperatorUpdateProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final AtomicReference<String> activeJavaJob = new AtomicReference<>();
    private final Object startLock = new Object();

    @Autowired
    public OperatorUpdateService(
            OperatorUpdateReleaseService releaseService,
            OperatorUpdateArtifactDownloader downloader,
            OperatorUpdateProperties properties,
            ObjectMapper objectMapper) {
        this(
                releaseService,
                downloader,
                properties,
                objectMapper,
                Executors.newVirtualThreadPerTaskExecutor(),
                Clock.systemUTC(),
                new SecureRandom());
    }

    OperatorUpdateService(
            OperatorUpdateReleaseService releaseService,
            OperatorUpdateArtifactDownloader downloader,
            OperatorUpdateProperties properties,
            ObjectMapper objectMapper,
            ExecutorService executor,
            Clock clock,
            SecureRandom secureRandom) {
        this.releaseService = Objects.requireNonNull(releaseService);
        this.downloader = Objects.requireNonNull(downloader);
        this.properties = Objects.requireNonNull(properties);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.executor = Objects.requireNonNull(executor);
        this.clock = Objects.requireNonNull(clock);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    /**
     * Reconstructs the latest durable job after a browser or API restart.
     *
     * <p>This path is deliberately read-only: it never creates the state root
     * or any child directory, and it remains available while new update
     * execution is disabled. Worker-owned status wins over Java staging status
     * for the same job ID.</p>
     */
    public OperatorUpdateOverviewDto overview() {
        String currentVersion = safeText(properties.getSoftwareVersion());
        boolean realMode =
                MongoSessionModeContext.currentMode() == MongoSessionMode.REAL;
        if (!realMode) {
            return new OperatorUpdateOverviewDto(
                    false,
                    currentVersion,
                    null);
        }

        Path state = normalizeAbsolute(
                properties.getStateDir(),
                "operator update state directory");
        if (!Files.exists(state, LinkOption.NOFOLLOW_LINKS)) {
            return new OperatorUpdateOverviewDto(
                    properties.isEnabled(),
                    currentVersion,
                    null);
        }
        requireSafeDirectory(state, "operator update state directory");

        Map<String, DurableStatusCandidate> statuses = new HashMap<>();
        discoverStatuses(state.resolve("staging"), false, statuses);
        discoverStatuses(state.resolve("jobs"), true, statuses);
        DurableStatusCandidate latest = null;
        for (DurableStatusCandidate candidate : statuses.values()) {
            if (latest == null || compareCandidates(candidate, latest) > 0) {
                latest = candidate;
            }
        }
        return new OperatorUpdateOverviewDto(
                properties.isEnabled(),
                currentVersion,
                latest == null ? null : latest.status(),
                rollbackPoint(state, currentVersion));
    }

    private OperatorRollbackDto rollbackPoint(Path state, String currentVersion) {
        Path receipt = state.resolve("runtime").resolve("recovery.json");
        if (!isSafeRegularFile(receipt)) return null;
        try {
            if (Files.size(receipt) > 4096) return null;
            var point = objectMapper.readValue(Files.readAllBytes(receipt), OperatorRollbackDto.class);
            if (!JOB_ID.matcher(safeText(point.jobId())).matches()
                    || !RELEASE_VERSION.matcher(safeText(point.targetVersion())).matches()
                    || !safeText(point.artifactSha256()).matches("sha256:[0-9a-f]{64}")
                    || !Objects.equals(currentVersion, point.fromVersion())) return null;
            return point;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    public OperatorUpdateJobDto rollback(String previousJobId, String actorUserId) {
        requireEnabledRealMode();
        String jobId = requireJobId(previousJobId);
        String actor = requireActor(actorUserId);
        var point = rollbackPoint(existingStateDirectory(), safeText(properties.getSoftwareVersion()));
        if (point == null || !point.jobId().equals(jobId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The retained rollback point is unavailable.");
        }
        var announcement = releaseService.findVersion(point.targetVersion());
        var manifest = announcement.updateManifest();
        if (manifest == null || !point.artifactSha256().equals(manifest.artifactSha256())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The previous release package has changed.");
        }
        VerifiedRelease verified = requireInstallableRelease(announcement, true);
        return start(new VerifiedRelease(verified.announcementId(), verified.manifestHash(), verified.manifest(), jobId), actor);
    }

    public OperatorUpdateJobDto approve(
            OperatorUpdateApprovalDto approval,
            String actorUserId) {
        requireEnabledRealMode();
        if (approval == null) {
            throw badRequest("An update approval is required.");
        }
        String announcementId =
                ReleaseProtocol.requireAnnouncementId(safeText(approval.announcementId()));
        String actor = requireActor(actorUserId);

        // Resolve every security-sensitive field from a fresh signed release
        // snapshot before allocating a local job.
        VerifiedRelease release = findVerifiedRelease(announcementId);
        return start(release, actor);
    }

    private OperatorUpdateJobDto start(VerifiedRelease release, String actor) {
        synchronized (startLock) {
            Path state = requireStateDirectory();
            String busyJob = findBusyJob(state);
            if (StringUtils.hasText(busyJob)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Update job " + busyJob + " is still active.");
            }
            requireStagingCapacity(
                    state.resolve("staging"),
                    release.manifest().artifactSizeBytes());
            String jobId = nextJobId();
            Path stage = createStageDirectory(state, jobId);
            OperatorUpdateJobDto initial = status(
                    jobId,
                    "CHECKING",
                    5,
                    "Checking the signed update manifest.",
                    now(),
                    actor,
                    release);
            writeStageStatus(stage, initial);
            // Return the capability only to the approving Operator. Persist its digest,
            // never the bearer, and never put the bearer in durable status/events.
            String monitorToken = null;
            if (properties.isMonitorEnabled()) {
                monitorToken = randomHex(32);
                atomicCreateJson(stage.resolve("monitor.json"), Map.of(
                        "jobId", jobId,
                        "tokenSha256", HexFormat.of().formatHex(sha256Digest().digest(
                                monitorToken.getBytes(StandardCharsets.US_ASCII))),
                        "expiresAt", clock.instant().plus(1, ChronoUnit.HOURS).getEpochSecond()));
            }
            activeJavaJob.set(jobId);
            executor.submit(() -> stageUpdate(state, stage, initial, release));
            return initial.withMonitorToken(monitorToken);
        }
    }

    public OperatorUpdateJobDto status(String suppliedJobId) {
        requireRealMode();
        String jobId = requireJobId(suppliedJobId);
        Path state = existingStateDirectory();
        Path workerStatus = state.resolve("jobs").resolve(jobId).resolve("status.json");
        if (isSafeRegularFile(workerStatus)) {
            return readStatus(workerStatus, jobId);
        }
        OperatorUpdateJobDto rejected = rejectedStatus(state, jobId);
        if (rejected != null) {
            return rejected;
        }
        Path stageStatus =
                state.resolve("staging").resolve(jobId).resolve("status.json");
        if (isSafeRegularFile(stageStatus)) {
            return readStatus(stageStatus, jobId);
        }
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "The update job was not found.");
    }

    public OperatorUpdateEventPageDto events(
            String suppliedJobId,
            Long suppliedAfter,
            Integer suppliedLimit) {
        requireRealMode();
        String jobId = requireJobId(suppliedJobId);
        long after = suppliedAfter == null ? 0L : suppliedAfter;
        int limit = suppliedLimit == null ? 50 : suppliedLimit;
        if (after < 0L || limit < 1 || limit > 100) {
            throw badRequest(
                    "Update event cursor must be non-negative and limit must be between 1 and 100.");
        }
        Path state = existingStateDirectory();
        List<OperatorUpdateJobDto> statuses = new ArrayList<>();
        readEvents(
                state.resolve("staging").resolve(jobId).resolve("events.ndjson"),
                jobId,
                statuses);
        readEvents(
                state.resolve("jobs").resolve(jobId).resolve("events.ndjson"),
                jobId,
                statuses);
        OperatorUpdateJobDto current = status(jobId);
        if (statuses.isEmpty()
                || !sameEvent(statuses.getLast(), current)) {
            statuses.add(current);
        }
        long total = statuses.size();
        List<OperatorUpdateEventDto> items = new ArrayList<>();
        for (long index = after; index < total && items.size() < limit; index++) {
            items.add(new OperatorUpdateEventDto(index + 1L, statuses.get((int) index)));
        }
        long nextAfter = items.isEmpty()
                ? Math.min(after, total)
                : items.getLast().sequence();
        return new OperatorUpdateEventPageDto(
                List.copyOf(items),
                nextAfter,
                TERMINAL_PHASES.contains(current.phase()));
    }

    private VerifiedRelease findVerifiedRelease(String announcementId) {
        return requireInstallableRelease(releaseService.find(announcementId), false);
    }

    private VerifiedRelease requireInstallableRelease(
            OperatorAnnouncementDto announcement, boolean rollback) {
        OperatorUpdateManifestDto manifest = announcement.updateManifest();
        if (!"UPDATE".equals(announcement.kind())
                || manifest == null
                || !ReleaseProtocol.UPDATE_MANIFEST_VERSION.equals(
                        manifest.manifestVersion())
                || !"AVAILABLE".equals(manifest.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected update is not available for installation.");
        }
        if (!manifest.packageSigningKeyConfigured()
                || !manifest.packageSigningKeyMatches()
                || !manifest.packageSignatureVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected update does not match the locally pinned release key.");
        }
        CanonicalValues.requireHash(
                announcement.updateManifestHash(),
                "update manifest hash");
        CanonicalValues.requireHash(manifest.artifactSha256(), "artifact_sha256");
        ReleaseProtocol.requirePackageSigningKeyId(manifest.packageSigningKeyId());
        if (!RELEASE_VERSION.matcher(safeText(manifest.releaseVersion())).matches()
                || !CHANNEL.matcher(safeText(manifest.channel())).matches()) {
            throw badRequest("The signed update version or channel is invalid.");
        }
        ReleaseProtocol.SemanticVersion releaseVersion =
                ReleaseProtocol.requireSemanticVersion(
                        manifest.releaseVersion(),
                        "release_version");
        ReleaseProtocol.SemanticVersion minimumVersion =
                ReleaseProtocol.requireSemanticVersion(
                        manifest.minimumCompatibleVersion(),
                        "minimum_compatible_version");
        ReleaseProtocol.SemanticVersion maximumVersion =
                ReleaseProtocol.requireSemanticVersion(
                        manifest.maximumCompatibleVersion(),
                        "maximum_compatible_version");
        ReleaseProtocol.SemanticVersion currentVersion;
        try {
            currentVersion = ReleaseProtocol.requireSemanticVersion(
                    safeText(properties.getSoftwareVersion()),
                    "current software version");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The installed software version is unavailable for compatibility checking.",
                    exception);
        }
        if (!rollback && (ReleaseProtocol.compareSemanticVersions(
                        currentVersion,
                        minimumVersion) < 0
                || ReleaseProtocol.compareSemanticVersions(
                        currentVersion,
                        maximumVersion) > 0)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected update is incompatible with the installed version.");
        }
        if (!rollback && ReleaseProtocol.compareSemanticVersions(
                releaseVersion,
                currentVersion) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected update is not newer than the installed version.");
        }
        if (manifest.artifactSizeBytes() < 1L
                || manifest.artifactSizeBytes() > properties.getMaxArtifactBytes()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The signed update artifact exceeds the configured download limit.");
        }
        OperatorUpdateArtifactDownloader.requireHttpsArtifactUri(manifest.artifactUrl());
        return new VerifiedRelease(
                announcement.announcementId(),
                announcement.updateManifestHash(),
                manifest);
    }

    private void stageUpdate(
            Path state,
            Path stage,
            OperatorUpdateJobDto initial,
            VerifiedRelease release) {
        Path partial = stage.resolve(".artifact.part");
        Path artifact = stage.resolve("myscoutee.deb");
        boolean requestPublished = false;
        try {
            OperatorUpdateJobDto downloading = status(
                    initial.jobId(),
                    "DOWNLOADING",
                    10,
                    "Downloading the registry-signed Debian package.",
                    initial.requestedAt(),
                    initial.requestedBy(),
                    release);
            writeStageStatus(stage, downloading);
            int[] lastPercent = {10};
            downloader.download(
                    release.manifest().artifactUrl(),
                    partial,
                    release.manifest().artifactSizeBytes(),
                    properties.getMaxArtifactBytes(),
                    bytes -> {
                        int percent = 10 + (int) Math.min(
                                50L,
                                bytes * 50L / release.manifest().artifactSizeBytes());
                        if (percent >= lastPercent[0] + 5) {
                            lastPercent[0] = percent;
                            writeStageStatus(stage, status(
                                    initial.jobId(),
                                    "DOWNLOADING",
                                    percent,
                                    "Downloading the registry-signed Debian package.",
                                    initial.requestedAt(),
                                    initial.requestedBy(),
                                    release));
                        }
                    });
            verifyDownloadedArtifact(partial, release.manifest());
            atomicCreateMove(partial, artifact);
            setOwnerOnlyPermissions(artifact);
            forceDirectory(stage);
            OperatorUpdateJobDto handingOff = status(
                    initial.jobId(),
                    "STAGING",
                    65,
                    "Publishing the verified package to the local update helper.",
                    initial.requestedAt(),
                    initial.requestedBy(),
                    release);
            writeStageStatus(stage, handingOff);
            publishRequest(state, handingOff, release);
            requestPublished = true;
            writeStageStatus(stage, status(
                    initial.jobId(),
                    "AWAITING_HOST",
                    70,
                    "Waiting for the local privileged update helper.",
                    initial.requestedAt(),
                    initial.requestedBy(),
                    release));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failBeforeHandoff(stage, initial, release, requestPublished, exception);
        } catch (RuntimeException | IOException exception) {
            failBeforeHandoff(stage, initial, release, requestPublished, exception);
        } finally {
            if (!requestPublished) {
                try {
                    Files.deleteIfExists(partial);
                } catch (IOException cleanupFailure) {
                    LOGGER.warn(
                            "Unable to remove partial update artifact for {}.",
                            initial.jobId(),
                            cleanupFailure);
                }
            }
            activeJavaJob.compareAndSet(initial.jobId(), null);
        }
    }

    private void failBeforeHandoff(
            Path stage,
            OperatorUpdateJobDto initial,
            VerifiedRelease release,
            boolean requestPublished,
            Exception failure) {
        LOGGER.warn(
                "Verified update staging failed for {}.",
                initial.jobId(),
                failure);
        if (requestPublished) {
            return;
        }
        try {
            writeStageStatus(stage, status(
                    initial.jobId(),
                    "FAILED",
                    100,
                    "The verified update could not be staged.",
                    initial.requestedAt(),
                    initial.requestedBy(),
                    release));
        } catch (RuntimeException statusFailure) {
            failure.addSuppressed(statusFailure);
            LOGGER.error(
                    "Unable to persist failed update status for {}.",
                    initial.jobId(),
                    statusFailure);
        }
    }

    private void verifyDownloadedArtifact(
            Path partial,
            OperatorUpdateManifestDto manifest) throws IOException {
        if (!isSafeRegularFile(partial)) {
            throw new IOException("The staged update artifact is unavailable.");
        }
        long size = Files.size(partial);
        if (size != manifest.artifactSizeBytes()
                || size > properties.getMaxArtifactBytes()) {
            throw new IOException(
                    "The staged update artifact size differs from the signed manifest.");
        }
        String digest = "sha256:" + sha256(partial);
        if (!MessageDigest.isEqual(
                digest.getBytes(StandardCharsets.US_ASCII),
                manifest.artifactSha256().getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException(
                    "The staged update artifact digest differs from the signed manifest.");
        }
    }

    private void publishRequest(
            Path state,
            OperatorUpdateJobDto job,
            VerifiedRelease release) {
        UpdateRequest request = new UpdateRequest(
                1,
                job.jobId(),
                job.requestedAt(),
                job.requestedBy(),
                release.manifestHash(),
                release.manifest().releaseVersion(),
                release.manifest().channel(),
                "myscoutee.deb",
                release.manifest().artifactSizeBytes(),
                release.manifest().artifactSha256(),
                release.manifest().packageSigningKeyId(),
                release.manifest().packageSignature(),
                release.rollbackJobId());
        Path requestFile = state.resolve("requests").resolve(job.jobId() + ".json");
        atomicCreateJson(requestFile, request);
    }

    private OperatorUpdateJobDto status(
            String jobId,
            String phase,
            int percent,
            String message,
            String requestedAt,
            String actor,
            VerifiedRelease release) {
        return new OperatorUpdateJobDto(
                1,
                jobId,
                phase,
                percent,
                message,
                now(),
                requestedAt,
                actor,
                release.manifestHash(),
                safeText(properties.getSoftwareVersion()),
                release.manifest().releaseVersion(),
                release.manifest().channel(),
                release.manifest().artifactSha256(),
                null);
    }

    private void writeStageStatus(
            Path stage,
            OperatorUpdateJobDto status) {
        atomicReplaceJson(stage.resolve("status.json"), status);
        Path events = stage.resolve("events.ndjson");
        try {
            byte[] json = objectMapper.writeValueAsBytes(status);
            try (FileChannel channel = FileChannel.open(
                    events,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                channel.write(ByteBuffer.wrap(json));
                channel.write(ByteBuffer.wrap(new byte[] {'\n'}));
                channel.force(true);
            }
            setOwnerOnlyPermissions(events);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to persist the update event journal.",
                    exception);
        }
    }

    private OperatorUpdateJobDto readStatus(Path path, String jobId) {
        try {
            if (Files.size(path) > 64 * 1024L) {
                throw new IllegalStateException("Update status file is too large.");
            }
            OperatorUpdateJobDto status =
                    objectMapper.readValue(path.toFile(), OperatorUpdateJobDto.class);
            validateStatus(status, jobId);
            return status;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read the durable update status.",
                    exception);
        }
    }

    private void discoverStatuses(
            Path directory,
            boolean workerOwned,
            Map<String, DurableStatusCandidate> output) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireSafeDirectory(directory, "operator update status directory");
        try (DirectoryStream<Path> entries =
                Files.newDirectoryStream(directory)) {
            int count = 0;
            for (Path entry : entries) {
                if (++count > MAX_DISCOVERY_ENTRIES) {
                    throw new IllegalStateException(
                            "Operator update discovery exceeds the entry limit.");
                }
                String jobId = entry.getFileName().toString();
                if (!JOB_ID.matcher(jobId).matches()
                        || !isSafeDirectory(entry)) {
                    continue;
                }
                Path statusFile = entry.resolve("status.json");
                if (!isSafeRegularFile(statusFile)) {
                    continue;
                }
                try {
                    OperatorUpdateJobDto status = readStatus(statusFile, jobId);
                    DurableStatusCandidate candidate =
                            durableCandidate(status, workerOwned);
                    if (workerOwned || !output.containsKey(jobId)) {
                        output.put(jobId, candidate);
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "Ignoring an invalid durable update status for {}.",
                            jobId);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to discover durable update jobs.",
                    exception);
        }
    }

    private static DurableStatusCandidate durableCandidate(
            OperatorUpdateJobDto status,
            boolean workerOwned) {
        try {
            Instant updatedAt = Instant.parse(safeText(status.updatedAt()));
            Instant requestedAt = Instant.parse(safeText(status.requestedAt()));
            return new DurableStatusCandidate(
                    status,
                    updatedAt,
                    requestedAt,
                    workerOwned);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Durable update status timestamps are invalid.",
                    exception);
        }
    }

    private static int compareCandidates(
            DurableStatusCandidate left,
            DurableStatusCandidate right) {
        int updated = left.updatedAt().compareTo(right.updatedAt());
        if (updated != 0) {
            return updated;
        }
        int requested = left.requestedAt().compareTo(right.requestedAt());
        if (requested != 0) {
            return requested;
        }
        int worker = Boolean.compare(left.workerOwned(), right.workerOwned());
        if (worker != 0) {
            return worker;
        }
        return left.status().jobId().compareTo(right.status().jobId());
    }

    private void readEvents(
            Path path,
            String jobId,
            List<OperatorUpdateJobDto> output) {
        if (!isSafeRegularFile(path)) {
            return;
        }
        try {
            if (Files.size(path) > MAX_EVENT_FILE_BYTES) {
                throw new IllegalStateException(
                        "Update event journal is too large.");
            }
            try (BufferedReader reader = Files.newBufferedReader(
                    path,
                    StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    if (++count > MAX_EVENT_LINES) {
                        throw new IllegalStateException(
                                "Update event journal has too many entries.");
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        OperatorUpdateJobDto status = objectMapper.readValue(
                                line,
                                OperatorUpdateJobDto.class);
                        validateStatus(status, jobId);
                        output.add(status);
                    } catch (RuntimeException exception) {
                        // A concurrent append can expose one incomplete final
                        // line. Reconnect will read it after the fsync.
                        break;
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to read the durable update event journal.",
                    exception);
        }
    }

    private OperatorUpdateJobDto rejectedStatus(Path state, String jobId) {
        Path rejectedDirectory = state.resolve("requests").resolve("rejected");
        if (!isSafeDirectory(rejectedDirectory)) {
            return null;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(
                rejectedDirectory,
                "*-" + jobId + ".json")) {
            if (files.iterator().hasNext()) {
                Path stageStatus =
                        state.resolve("staging").resolve(jobId).resolve("status.json");
                OperatorUpdateJobDto previous = isSafeRegularFile(stageStatus)
                        ? readStatus(stageStatus, jobId)
                        : null;
                return new OperatorUpdateJobDto(
                        1,
                        jobId,
                        "REJECTED",
                        100,
                        "The local update helper rejected the staged request.",
                        now(),
                        previous == null ? null : previous.requestedAt(),
                        previous == null ? null : previous.requestedBy(),
                        previous == null ? null : previous.manifestHash(),
                        previous == null ? null : previous.currentVersion(),
                        previous == null ? null : previous.targetVersion(),
                        previous == null ? null : previous.channel(),
                        previous == null ? null : previous.artifactSha256(),
                        null);
            }
            return null;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect rejected update requests.",
                    exception);
        }
    }

    private String findBusyJob(Path state) {
        String active = activeJavaJob.get();
        if (StringUtils.hasText(active)) {
            return active;
        }
        Path requests = state.resolve("requests");
        try (DirectoryStream<Path> files =
                Files.newDirectoryStream(requests, "upd_*.json")) {
            for (Path file : files) {
                if (isSafeRegularFile(file)) {
                    return file.getFileName().toString().replace(".json", "");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect pending update requests.",
                    exception);
        }
        Path jobs = state.resolve("jobs");
        try (DirectoryStream<Path> directories =
                Files.newDirectoryStream(jobs, "upd_*")) {
            int count = 0;
            for (Path directory : directories) {
                if (++count > 10_000) {
                    throw new IllegalStateException(
                            "Update job directory contains too many entries.");
                }
                String jobId = directory.getFileName().toString();
                Path statusFile = directory.resolve("status.json");
                if (JOB_ID.matcher(jobId).matches()
                        && isSafeRegularFile(statusFile)
                        && !TERMINAL_PHASES.contains(
                                readStatus(statusFile, jobId).phase())) {
                    return jobId;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect host update jobs.",
                    exception);
        }
        return null;
    }

    private Path requireStateDirectory() {
        Path state = normalizeAbsolute(
                properties.getStateDir(),
                "operator update state directory");
        try {
            Files.createDirectories(state);
            requireSafeDirectory(state, "operator update state directory");
            Path staging = state.resolve("staging");
            Path requests = state.resolve("requests");
            Path jobs = state.resolve("jobs");
            Files.createDirectories(staging);
            Files.createDirectories(requests);
            Files.createDirectories(jobs);
            requireSafeDirectory(staging, "operator update staging directory");
            requireSafeDirectory(requests, "operator update request directory");
            requireSafeDirectory(jobs, "operator update job directory");
            return state;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to access the shared operator update state.",
                    exception);
        }
    }

    private Path existingStateDirectory() {
        Path state = normalizeAbsolute(
                properties.getStateDir(),
                "operator update state directory");
        if (!Files.exists(state, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "The update job was not found.");
        }
        requireSafeDirectory(state, "operator update state directory");
        return state;
    }

    private Path createStageDirectory(Path state, String jobId) {
        Path stage = state.resolve("staging").resolve(jobId);
        try {
            Files.createDirectory(stage);
            setOwnerOnlyPermissions(stage);
            forceDirectory(stage.getParent());
            return stage;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to create the update staging directory.",
                    exception);
        }
    }

    private void requireStagingCapacity(
            Path stagingDirectory,
            long artifactSize) {
        long reserve = properties.getStagingSafetyReserveBytes();
        long required;
        try {
            required = Math.addExact(artifactSize, reserve);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The configured update staging reserve is invalid.",
                    exception);
        }
        try {
            long available = Files.getFileStore(stagingDirectory)
                    .getUsableSpace();
            if (available < required) {
                throw new ResponseStatusException(
                        HttpStatus.INSUFFICIENT_STORAGE,
                        "There is not enough staging space for the verified update and safety reserve.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect update staging capacity.",
                    exception);
        }
    }

    private void atomicCreateJson(Path target, Object value) {
        byte[] json = objectMapper.writeValueAsBytes(value);
        Path temporary = target.getParent().resolve(
                "." + target.getFileName() + ".part-" + randomHex(8));
        try {
            writeNewFile(temporary, json);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            setOwnerOnlyPermissions(target);
            forceDirectory(target.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to atomically publish the host update request.",
                    exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private void atomicReplaceJson(Path target, Object value) {
        byte[] json = objectMapper.writeValueAsBytes(value);
        Path temporary = target.getParent().resolve(
                "." + target.getFileName() + ".part-" + randomHex(8));
        try {
            writeNewFile(temporary, json);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            setOwnerOnlyPermissions(target);
            forceDirectory(target.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to atomically persist the update status.",
                    exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private static void writeNewFile(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        setOwnerOnlyPermissions(path);
    }

    private static void atomicCreateMove(Path source, Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void validateStatus(
            OperatorUpdateJobDto status,
            String expectedJobId) {
        if (status == null
                || status.schemaVersion() != 1
                || !expectedJobId.equals(status.jobId())
                || !StringUtils.hasText(status.phase())
                || status.percent() < 0
                || status.percent() > 100) {
            throw new IllegalStateException(
                    "Durable update status is invalid.");
        }
    }

    private void requireEnabledRealMode() {
        requireRealMode();
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Operator-approved package updates are disabled.");
        }
    }

    private void requireRealMode() {
        if (MongoSessionModeContext.currentMode() != MongoSessionMode.REAL) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Package installation is unavailable in Explore mode.");
        }
    }

    private static String requireActor(String value) {
        String actor = safeText(value);
        if (actor.isEmpty()
                || actor.length() > 160
                || actor.indexOf('\n') >= 0
                || actor.indexOf('\r') >= 0) {
            throw badRequest("The authenticated operator identity is invalid.");
        }
        return actor;
    }

    private static String requireJobId(String value) {
        String jobId = safeText(value);
        if (!JOB_ID.matcher(jobId).matches()) {
            throw badRequest("The update job ID is invalid.");
        }
        return jobId;
    }

    private static Path normalizeAbsolute(Path path, String label) {
        if (path == null || !path.isAbsolute()) {
            throw new IllegalStateException(label + " must be absolute.");
        }
        return path.normalize();
    }

    private static void requireSafeDirectory(Path path, String label) {
        if (!isSafeDirectory(path)) {
            throw new IllegalStateException(
                    label + " must be a regular non-symlink directory.");
        }
    }

    private static boolean isSafeDirectory(Path path) {
        return path != null
                && !Files.isSymbolicLink(path)
                && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isSafeRegularFile(Path path) {
        return path != null
                && !Files.isSymbolicLink(path)
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void setOwnerOnlyPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    path,
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            ? PosixFilePermissions.fromString("rwx------")
                            : PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // The production volume is POSIX; non-POSIX test filesystems still
            // retain Java's create-new and non-symlink safety checks.
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(
                directory,
                StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void deleteTemporary(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The target operation already reports its primary failure.
        }
    }

    private String nextJobId() {
        return "upd_" + randomHex(16);
    }

    private String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private String now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static boolean sameEvent(
            OperatorUpdateJobDto first,
            OperatorUpdateJobDto second) {
        return Objects.equals(first.phase(), second.phase())
                && first.percent() == second.percent()
                && Objects.equals(first.updatedAt(), second.updatedAt());
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @PreDestroy
    void closeExecutor() {
        executor.shutdownNow();
    }

    private record VerifiedRelease(
            String announcementId,
            String manifestHash,
            OperatorUpdateManifestDto manifest,
            String rollbackJobId) {
        VerifiedRelease(String announcementId, String manifestHash, OperatorUpdateManifestDto manifest) {
            this(announcementId, manifestHash, manifest, null);
        }
    }

    private record DurableStatusCandidate(
            OperatorUpdateJobDto status,
            Instant updatedAt,
            Instant requestedAt,
            boolean workerOwned) {
    }

    private record UpdateRequest(
            @JsonProperty("schema_version") int schemaVersion,
            @JsonProperty("job_id") String jobId,
            @JsonProperty("requested_at") String requestedAt,
            @JsonProperty("requested_by") String requestedBy,
            @JsonProperty("manifest_hash") String manifestHash,
            @JsonProperty("release_version") String releaseVersion,
            String channel,
            @JsonProperty("artifact_name") String artifactName,
            @JsonProperty("artifact_size_bytes") long artifactSizeBytes,
            @JsonProperty("artifact_sha256") String artifactSha256,
            @JsonProperty("package_signing_key_id") String packageSigningKeyId,
            @JsonProperty("package_signature") String packageSignature,
            @JsonProperty("rollback_job_id") String rollbackJobId) {
    }
}
