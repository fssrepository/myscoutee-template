package com.raxim.myscoutee.domain.platform.service;
import com.raxim.myscoutee.domain.platform.api.ReleaseProtocol;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateArtifactDownloader;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateReleaseService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.common.config.JsonConfig;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.config.OperatorUpdateProperties;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorAnnouncementDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorAnnouncementPageDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorAnnouncementSnapshotDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateManifestDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateApprovalDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateJobDto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@org.junit.jupiter.api.Tag("standard")
class OperatorUpdateServiceTest {
    private static final Instant NOW =
            Instant.parse("2026-07-28T19:30:00Z");
    private static final String ANNOUNCEMENT_ID =
            "ann_0123456789abcdef0123456789abcdef";
    private static final String MANIFEST_HASH =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PACKAGE_KEY_ID =
            "pkey_0123456789abcdef0123456789abcdef";
    private static final String PACKAGE_SIGNATURE =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                    + "AAAAAAAAAAAAAAAAAAAAAA==";

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void clearMode() {
        MongoSessionModeContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.3", "1.0.0-qa.3"})
    void stagesVerifiedArtifactPublishesExactRequestAndPrefersWorkerStatus(String targetVersion)
            throws Exception {
        byte[] artifact = "signed-debian-package".getBytes(StandardCharsets.UTF_8);
        String artifactHash = "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(artifact));
        ObjectMapper objectMapper = new JsonConfig().objectMapper();
        OperatorUpdateReleaseService networkService = mock(OperatorUpdateReleaseService.class);
        OperatorUpdateArtifactDownloader downloader =
                mock(OperatorUpdateArtifactDownloader.class);
        OperatorUpdateProperties properties = updateProperties();
        properties.setMonitorEnabled(true);
        properties.setSoftwareVersion("1.0.0-qa.1");
        OperatorAnnouncementPageDto announcements =
                announcementPage(artifact.length, artifactHash, true, targetVersion);
        when(networkService.find(ANNOUNCEMENT_ID))
                .thenReturn(announcements.items().getFirst());
        doAnswer(invocation -> {
            Path target = invocation.getArgument(1);
            Files.write(
                    target,
                    artifact,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            LongConsumer progress = invocation.getArgument(4);
            progress.accept(artifact.length);
            return null;
        }).when(downloader).download(
                any(),
                any(),
                anyLong(),
                anyLong(),
                any());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        OperatorUpdateService service = new OperatorUpdateService(
                networkService,
                downloader,
                properties,
                objectMapper,
                executor,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom());
        try {
            OperatorUpdateJobDto initial = service.approve(
                    new OperatorUpdateApprovalDto(ANNOUNCEMENT_ID),
                    "operator-user-1");
            assertThat(initial.monitorToken()).matches("[0-9a-f]{64}");
            Path monitor = properties.getStateDir().resolve("staging").resolve(initial.jobId()).resolve("monitor.json");
            JsonNode grant = objectMapper.readTree(monitor.toFile());
            assertThat(grant.path("jobId").asText()).isEqualTo(initial.jobId());
            assertThat(grant.path("tokenSha256").asText()).isEqualTo(java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(initial.monitorToken().getBytes(StandardCharsets.US_ASCII))));
            assertThat(grant.path("expiresAt").asLong()).isEqualTo(NOW.plusSeconds(3600).getEpochSecond());
            assertThat(Files.readString(monitor)).doesNotContain(initial.monitorToken());
            assertThat(initial.phase()).isEqualTo("CHECKING");
            assertThat(initial.jobId()).matches("^upd_[0-9a-f]{32}$");

            Path request = properties.getStateDir()
                    .resolve("requests")
                    .resolve(initial.jobId() + ".json");
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(request).isRegularFile());

            JsonNode requestJson = objectMapper.readTree(request.toFile());
            assertThat(requestJson.path("release_version").asText()).isEqualTo(targetVersion);
            assertThat(requestJson.path("schema_version").asInt()).isEqualTo(1);
            assertThat(requestJson.path("job_id").asText())
                    .isEqualTo(initial.jobId());
            assertThat(requestJson.path("requested_by").asText())
                    .isEqualTo("operator-user-1");
            assertThat(requestJson.path("manifest_hash").asText())
                    .isEqualTo(MANIFEST_HASH);
            assertThat(requestJson.path("channel").asText())
                    .isEqualTo("stable.lts_1");
            assertThat(requestJson.path("artifact_size_bytes").asLong())
                    .isEqualTo(artifact.length);
            assertThat(requestJson.path("artifact_sha256").asText())
                    .isEqualTo(artifactHash);
            assertThat(requestJson.path("package_signing_key_id").asText())
                    .isEqualTo(PACKAGE_KEY_ID);
            assertThat(requestJson.path("package_signature").asText())
                    .isEqualTo(PACKAGE_SIGNATURE);
            assertThat(requestJson.has("artifact_url")).isFalse();
            assertThat(requestJson.has("command")).isFalse();
            assertThat(requestJson.path("artifact_name").asText())
                    .isEqualTo("myscoutee.deb");
            assertThat(requestJson.has("artifact_path")).isFalse();
            Path stagedArtifact = properties.getStateDir()
                    .resolve("staging")
                    .resolve(initial.jobId())
                    .resolve(requestJson.path("artifact_name").asText());
            assertThat(stagedArtifact)
                    .hasBinaryContent(artifact);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(service.status(initial.jobId()).phase())
                            .isEqualTo("AWAITING_HOST"));
            assertThat(service.status(initial.jobId()).monitorToken()).isNull();
            assertThat(service.events(initial.jobId(), 0L, 100).items())
                    .extracting(event -> event.status().phase())
                    .containsSubsequence(
                            "CHECKING",
                            "DOWNLOADING",
                            "STAGING",
                            "AWAITING_HOST");

            OperatorUpdateJobDto workerStatus = new OperatorUpdateJobDto(
                    1,
                    initial.jobId(),
                    "VERIFYING",
                    72,
                    "Verifying the staged package.",
                    NOW.plusSeconds(1).toString(),
                    initial.requestedAt(),
                    initial.requestedBy(),
                    initial.manifestHash(),
                    "1.0.0",
                    "1.2.3",
                    "stable.lts_1",
                    artifactHash,
                    null);
            Path workerJob = properties.getStateDir()
                    .resolve("jobs")
                    .resolve(initial.jobId());
            Files.createDirectory(workerJob);
            Files.write(
                    workerJob.resolve("status.json"),
                    objectMapper.writeValueAsBytes(workerStatus));

            assertThat(service.status(initial.jobId()).phase())
                    .isEqualTo("VERIFYING");
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void refusesManifestWithoutPinnedPackageVerificationBeforeDownload() {
        ObjectMapper objectMapper = new JsonConfig().objectMapper();
        OperatorUpdateReleaseService networkService = mock(OperatorUpdateReleaseService.class);
        OperatorUpdateArtifactDownloader downloader =
                mock(OperatorUpdateArtifactDownloader.class);
        OperatorUpdateProperties properties = updateProperties();
        when(networkService.find(ANNOUNCEMENT_ID))
                .thenReturn(announcementPage(
                        10,
                        "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        false).items().getFirst());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        OperatorUpdateService service = new OperatorUpdateService(
                networkService,
                downloader,
                properties,
                objectMapper,
                executor,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom());
        try {
            assertThatThrownBy(() -> service.approve(
                    new OperatorUpdateApprovalDto(ANNOUNCEMENT_ID),
                    "operator-user-1"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("pinned release key");
            verify(downloader, never()).download(
                    any(),
                    any(),
                    anyLong(),
                    anyLong(),
                    any());
        } catch (Exception exception) {
            throw new AssertionError(exception);
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void exploreModeCannotStageHostPackageUpdate() {
        MongoSessionModeContext.set(MongoSessionMode.DEMO);
        OperatorUpdateReleaseService networkService = mock(OperatorUpdateReleaseService.class);
        OperatorUpdateArtifactDownloader downloader =
                mock(OperatorUpdateArtifactDownloader.class);
        OperatorUpdateProperties properties = updateProperties();
        OperatorUpdateService service = new OperatorUpdateService(
                networkService,
                downloader,
                properties,
                new JsonConfig().objectMapper());
        try {
            assertThatThrownBy(() -> service.approve(
                    new OperatorUpdateApprovalDto(ANNOUNCEMENT_ID),
                    "demo-operator"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Explore mode");
            verify(networkService, never()).find(any());
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void refusesIncompatibleInstalledVersionBeforeAllocatingJob() {
        ObjectMapper objectMapper = new JsonConfig().objectMapper();
        OperatorUpdateReleaseService networkService = mock(OperatorUpdateReleaseService.class);
        OperatorUpdateArtifactDownloader downloader =
                mock(OperatorUpdateArtifactDownloader.class);
        OperatorUpdateProperties properties = updateProperties();
        properties.setSoftwareVersion("3.0.0");
        when(networkService.find(ANNOUNCEMENT_ID))
                .thenReturn(announcementPage(
                        10,
                        "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        true).items().getFirst());
        OperatorUpdateService service = new OperatorUpdateService(
                networkService,
                downloader,
                properties,
                objectMapper);
        try {
            assertThatThrownBy(() -> service.approve(
                    new OperatorUpdateApprovalDto(ANNOUNCEMENT_ID),
                    "operator-user-1"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("incompatible");
            assertThat(properties.getStateDir()).doesNotExist();
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void requiresSignedArtifactSizePlusConfiguredStagingReserve()
            throws Exception {
        ObjectMapper objectMapper = new JsonConfig().objectMapper();
        OperatorUpdateReleaseService networkService = mock(OperatorUpdateReleaseService.class);
        OperatorUpdateArtifactDownloader downloader =
                mock(OperatorUpdateArtifactDownloader.class);
        OperatorUpdateProperties properties = updateProperties();
        when(networkService.find(ANNOUNCEMENT_ID))
                .thenReturn(announcementPage(
                        10,
                        "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        true).items().getFirst());
        Files.createDirectories(properties.getStateDir().resolve("staging"));
        // The signed artifact is 10 bytes; the valid sum is Long.MAX_VALUE.
        properties.setStagingSafetyReserveBytes(Long.MAX_VALUE - 10L);
        OperatorUpdateService service = new OperatorUpdateService(
                networkService,
                downloader,
                properties,
                objectMapper);
        try {
            assertThatThrownBy(() -> service.approve(
                    new OperatorUpdateApprovalDto(ANNOUNCEMENT_ID),
                    "operator-user-1"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("staging space");
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void overviewIsReadOnlyWhenStateHasNotBeenCreated() {
        OperatorUpdateProperties properties = updateProperties();
        properties.setEnabled(false);
        OperatorUpdateService service = new OperatorUpdateService(
                mock(OperatorUpdateReleaseService.class),
                mock(OperatorUpdateArtifactDownloader.class),
                properties,
                new JsonConfig().objectMapper());
        try {
            var overview = service.overview();

            assertThat(overview.enabled()).isFalse();
            assertThat(overview.currentVersion()).isEqualTo("1.0.0");
            assertThat(overview.latestJob()).isNull();
            assertThat(properties.getStateDir()).doesNotExist();
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void overviewReconnectsToLatestDurableJobAndPrefersWorkerStatus()
            throws Exception {
        ObjectMapper objectMapper = new JsonConfig().objectMapper();
        OperatorUpdateProperties properties = updateProperties();
        String jobId = "upd_0123456789abcdef0123456789abcdef";
        Path stage = properties.getStateDir().resolve("staging").resolve(jobId);
        Path worker = properties.getStateDir().resolve("jobs").resolve(jobId);
        Files.createDirectories(stage);
        Files.createDirectories(worker);
        OperatorUpdateJobDto stageStatus = durableStatus(
                jobId,
                "AWAITING_HOST",
                "2026-07-28T19:31:00Z");
        OperatorUpdateJobDto workerStatus = durableStatus(
                jobId,
                "VERIFYING",
                "2026-07-28T19:30:30Z");
        Files.write(
                stage.resolve("status.json"),
                objectMapper.writeValueAsBytes(stageStatus));
        Files.write(
                worker.resolve("status.json"),
                objectMapper.writeValueAsBytes(workerStatus));
        OperatorUpdateService service = new OperatorUpdateService(
                mock(OperatorUpdateReleaseService.class),
                mock(OperatorUpdateArtifactDownloader.class),
                properties,
                objectMapper);
        try {
            var overview = service.overview();

            assertThat(overview.enabled()).isTrue();
            assertThat(overview.latestJob()).isNotNull();
            assertThat(overview.latestJob().jobId()).isEqualTo(jobId);
            assertThat(overview.latestJob().phase()).isEqualTo("VERIFYING");
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void statusAndEventsRemainReadOnlyAndReconnectableWhenUpdatesAreDisabled()
            throws Exception {
        ObjectMapper objectMapper = new JsonConfig().objectMapper();
        OperatorUpdateProperties properties = updateProperties();
        properties.setEnabled(false);
        String jobId = "upd_fedcba9876543210fedcba9876543210";
        Path worker = properties.getStateDir().resolve("jobs").resolve(jobId);
        Files.createDirectories(worker);
        OperatorUpdateJobDto recovery = durableStatus(
                jobId,
                "RECOVERY_REQUIRED",
                "2026-07-28T19:32:00Z");
        Files.write(
                worker.resolve("status.json"),
                objectMapper.writeValueAsBytes(recovery));
        Files.writeString(
                worker.resolve("events.ndjson"),
                objectMapper.writeValueAsString(recovery) + "\n");
        OperatorUpdateService service = new OperatorUpdateService(
                mock(OperatorUpdateReleaseService.class),
                mock(OperatorUpdateArtifactDownloader.class),
                properties,
                objectMapper);
        try {
            assertThat(service.status(jobId).phase())
                    .isEqualTo("RECOVERY_REQUIRED");
            assertThat(service.events(jobId, 0L, 10).terminal()).isTrue();
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void missingStatusLookupDoesNotCreateUpdateState() {
        OperatorUpdateProperties properties = updateProperties();
        properties.setEnabled(false);
        OperatorUpdateService service = new OperatorUpdateService(
                mock(OperatorUpdateReleaseService.class),
                mock(OperatorUpdateArtifactDownloader.class),
                properties,
                new JsonConfig().objectMapper());
        try {
            assertThatThrownBy(() -> service.status(
                    "upd_fedcba9876543210fedcba9876543210"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not found");
            assertThat(properties.getStateDir()).doesNotExist();
        } finally {
            service.closeExecutor();
        }
    }

    @Test
    void rollbackStagesOnlyTheExactRetainedPackageAndBindsTheWorkerRequest() throws Exception {
        byte[] artifact = "retained-package".getBytes(StandardCharsets.UTF_8);
        String hash = "sha256:" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
        var properties = updateProperties();
        properties.setSoftwareVersion("1.2.4");
        Path receipt = properties.getStateDir().resolve("runtime/recovery.json");
        Files.createDirectories(receipt.getParent());
        String previousJob = "upd_12345678901234567890123456789012";
        var mapper = new JsonConfig().objectMapper();
        Files.writeString(receipt, mapper.writeValueAsString(java.util.Map.of(
                "jobId", previousJob, "fromVersion", "1.2.4", "targetVersion", "1.2.3", "artifactSha256", hash)));
        var releases = mock(OperatorUpdateReleaseService.class);
        when(releases.findVersion("1.2.3")).thenReturn(announcementPage(artifact.length, hash, true, "1.2.3").items().getFirst());
        var downloader = mock(OperatorUpdateArtifactDownloader.class);
        doAnswer(invocation -> { Files.write((Path) invocation.getArgument(1), artifact); return null; })
                .when(downloader).download(any(), any(), anyLong(), anyLong(), any());
        var service = new OperatorUpdateService(releases, downloader, properties, mapper);
        try {
            assertThat(service.overview().rollback().targetVersion()).isEqualTo("1.2.3");
            assertThatThrownBy(() -> service.rollback("upd_00000000000000000000000000000000", "operator"))
                    .isInstanceOf(ResponseStatusException.class);
            when(releases.findVersion("1.2.3")).thenReturn(announcementPage(artifact.length, "sha256:" + "b".repeat(64), true, "1.2.3").items().getFirst());
            assertThatThrownBy(() -> service.rollback(previousJob, "operator")).hasMessageContaining("package has changed");
            when(releases.findVersion("1.2.3")).thenReturn(announcementPage(artifact.length, hash, true, "1.2.3").items().getFirst());
            var job = service.rollback(previousJob, "operator");
            Path request = properties.getStateDir().resolve("requests").resolve(job.jobId() + ".json");
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(request).isRegularFile());
            assertThat(mapper.readTree(request.toFile()).path("rollback_job_id").asText()).isEqualTo(previousJob);
            assertThat(mapper.readTree(request.toFile()).path("artifact_sha256").asText()).isEqualTo(hash);
        } finally { service.closeExecutor(); }
    }

    private OperatorUpdateJobDto durableStatus(
            String jobId,
            String phase,
            String updatedAt) {
        return new OperatorUpdateJobDto(
                1,
                jobId,
                phase,
                72,
                "Durable status.",
                updatedAt,
                "2026-07-28T19:30:00Z",
                "operator-user-1",
                MANIFEST_HASH,
                "1.0.0",
                "1.2.3",
                "stable",
                "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                null);
    }

    private OperatorUpdateProperties updateProperties() {
        OperatorUpdateProperties properties = new OperatorUpdateProperties();
        properties.setEnabled(true);
        properties.setSoftwareVersion("1.0.0");
        properties.setStateDir(temporaryDirectory.resolve("update-state"));
        properties.setReleasePublicKeyPath(
                temporaryDirectory.resolve("release-public-key.pem"));
        properties.setMaxArtifactBytes(1_048_576L);
        properties.setStagingSafetyReserveBytes(0L);
        return properties;
    }


    private OperatorAnnouncementPageDto announcementPage(
            long artifactSize,
            String artifactHash,
            boolean packageVerified) {
        return announcementPage(artifactSize, artifactHash, packageVerified, "1.2.3");
    }

    private OperatorAnnouncementPageDto announcementPage(
            long artifactSize, String artifactHash, boolean packageVerified, String version) {
        OperatorUpdateManifestDto manifest = new OperatorUpdateManifestDto(
                ReleaseProtocol.UPDATE_MANIFEST_VERSION,
                version,
                "stable.lts_1",
                "2026-07-28T19:00:00Z",
                "1.0.0-qa.1",
                "2.0.0",
                "https://downloads.example.test/myscoutee_1.2.3_amd64.deb",
                artifactSize,
                artifactHash,
                PACKAGE_KEY_ID,
                PACKAGE_SIGNATURE,
                "https://downloads.example.test/releases/1.2.3",
                true,
                90,
                "AVAILABLE",
                null,
                null,
                null,
                true,
                true,
                packageVerified);
        OperatorAnnouncementDto announcement = new OperatorAnnouncementDto(
                1L,
                ANNOUNCEMENT_ID,
                "UPDATE",
                "NOTICE",
                manifest.publishedAt(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                manifest,
                MANIFEST_HASH,
                "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        return new OperatorAnnouncementPageDto(
                new OperatorAnnouncementSnapshotDto(
                        "annsnap_0123456789abcdef0123456789abcdef",
                        "2026-07-28T19:30:00Z",
                        1L,
                        announcement.announcementHash(),
                        "UPDATE",
                        "",
                        "",
                        false,
                        "2026-07-28T19:30:00Z",
                        "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"),
                List.of(announcement),
                null);
    }
}
