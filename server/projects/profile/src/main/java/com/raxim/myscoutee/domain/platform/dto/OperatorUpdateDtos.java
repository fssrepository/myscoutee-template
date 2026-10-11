package com.raxim.myscoutee.domain.platform.dto;

import java.util.List;

public final class OperatorUpdateDtos {
    private OperatorUpdateDtos() {
    }

    public record OperatorUpdateApprovalDto(
            String announcementId) {
    }

    public record OperatorUpdateJobDto(
            int schemaVersion,
            String jobId,
            String phase,
            int percent,
            String message,
            String updatedAt,
            String requestedAt,
            String requestedBy,
            String manifestHash,
            String currentVersion,
            String targetVersion,
            String channel,
            String artifactSha256,
            String backupPath,
            String monitorToken) {
        public OperatorUpdateJobDto(int schemaVersion, String jobId, String phase, int percent,
                String message, String updatedAt, String requestedAt, String requestedBy,
                String manifestHash, String currentVersion, String targetVersion, String channel,
                String artifactSha256, String backupPath) {
            this(schemaVersion, jobId, phase, percent, message, updatedAt, requestedAt, requestedBy,
                    manifestHash, currentVersion, targetVersion, channel, artifactSha256, backupPath, null);
        }

        public OperatorUpdateJobDto withMonitorToken(String token) {
            return new OperatorUpdateJobDto(schemaVersion, jobId, phase, percent, message, updatedAt,
                    requestedAt, requestedBy, manifestHash, currentVersion, targetVersion, channel,
                    artifactSha256, backupPath, token);
        }
    }

    public record OperatorRollbackDto(String jobId, String fromVersion, String targetVersion, String artifactSha256) {}

    public record OperatorUpdateOverviewDto(
            boolean enabled,
            String currentVersion,
            OperatorUpdateJobDto latestJob,
            OperatorRollbackDto rollback) {
        public OperatorUpdateOverviewDto(boolean enabled, String currentVersion, OperatorUpdateJobDto latestJob) {
            this(enabled, currentVersion, latestJob, null);
        }
    }

    public record OperatorUpdateEventDto(
            long sequence,
            OperatorUpdateJobDto status) {
    }

    public record OperatorUpdateEventPageDto(
            List<OperatorUpdateEventDto> items,
            long nextAfter,
            boolean terminal) {
    }

    public record OperatorAnnouncementLocalizationDto(
            String locale,
            String title,
            String body) {
    }

    public record OperatorAnnouncementLinkDto(
            String relation,
            String url) {
    }

    public record OperatorUpdateManifestDto(
            String manifestVersion,
            String releaseVersion,
            String channel,
            String publishedAt,
            String minimumCompatibleVersion,
            String maximumCompatibleVersion,
            String artifactUrl,
            long artifactSizeBytes,
            String artifactSha256,
            String packageSigningKeyId,
            String packageSignature,
            String releaseNotesUrl,
            boolean backupRequired,
            long expectedDowntimeSeconds,
            String status,
            String supersedesVersion,
            String supersededByVersion,
            String revocationReason,
            boolean packageSigningKeyConfigured,
            boolean packageSigningKeyMatches,
            boolean packageSignatureVerified) {
    }

    public record OperatorAnnouncementDto(
            long sequence,
            String announcementId,
            String kind,
            String severity,
            String publishedAt,
            String expiresAt,
            String titleKey,
            String bodyKey,
            List<OperatorAnnouncementLocalizationDto> localizations,
            List<OperatorAnnouncementLinkDto> links,
            OperatorUpdateManifestDto updateManifest,
            String updateManifestHash,
            String announcementHash) {
    }

    public record OperatorAnnouncementSnapshotDto(
            String snapshotId,
            String asOf,
            long throughSequence,
            String announcementHeadHash,
            String kind,
            String severity,
            String channel,
            boolean includeExpired,
            String createdAt,
            String snapshotHash) {
    }

    public record OperatorAnnouncementPageDto(
            OperatorAnnouncementSnapshotDto snapshot,
            List<OperatorAnnouncementDto> items,
            String nextCursor) {
    }
}
