package com.raxim.myscoutee.domain.help.dto;

import java.util.List;

public final class HelpCenterDtos {
    private HelpCenterDtos() {}

    public record ContentLanguageDto(String lang, String label) {}

    public record HelpCenterSectionDto(
            String id,
            String icon,
            String title,
            String blurb,
            String contentHtml,
            List<String> imageUrls,
            String panelSpan,
            Boolean optional,
            String guideStepId) {}

    public record HelpCenterRevisionDto(
            String id,
            String documentKind,
            String contextKey,
            String lang,
            String languageLabel,
            Integer version,
            String title,
            String summary,
            String description,
            String headerColor,
            String presentation,
            List<HelpCenterSectionDto> sections,
            Boolean active,
            String createdAtIso,
            String createdByUserId,
            String updatedAtIso,
            String updatedByUserId,
            boolean isSystem) {}

    public record HelpCenterAuditEntryDto(
            String id,
            String documentKind,
            String lang,
            String languageLabel,
            String revisionId,
            Integer version,
            String action,
            String actorUserId,
            String createdAtIso,
            String message) {}

    public record HelpCenterGuideFieldDto(
            String id,
            String screenKey,
            String group,
            String i18nKey,
            Integer order) {}

    public record HelpCenterStateDto(
            HelpCenterRevisionDto activeRevision,
            List<HelpCenterRevisionDto> revisions,
            List<HelpCenterAuditEntryDto> auditTrail,
            List<ContentLanguageDto> availableLanguages,
            List<HelpCenterGuideFieldDto> guideFields) {}

    public record PrivacyConsentDto(
            String id,
            String userId,
            String revisionId,
            Integer revisionVersion,
            List<String> approvedOptionalSectionIds,
            String acceptedAtIso,
            String updatedAtIso,
            String source) {}

    public record PrivacyConsentSaveRequestDto(
            String userId,
            String revisionId,
            Integer revisionVersion,
            List<String> approvedOptionalSectionIds,
            String source) {}

}
