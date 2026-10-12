package com.raxim.myscoutee.domain.help.service;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.help.document.HelpCenterGuideField;
import com.raxim.myscoutee.domain.help.document.HelpCenterRevision;
import com.raxim.myscoutee.domain.help.document.HelpCenterRevision.HelpCenterSection;
import com.raxim.myscoutee.domain.accounts.api.AccountConsents.Consent;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterGuideFieldDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterRevisionDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterSectionDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterStateDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.PrivacyConsentDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.PrivacyConsentSaveRequestDto;
import com.raxim.myscoutee.domain.help.repository.HelpCenterGuideFieldsRepository;
import com.raxim.myscoutee.domain.help.repository.HelpCenterRevisionsRepository;
import com.raxim.myscoutee.domain.accounts.api.AccountConsents;
import com.raxim.myscoutee.domain.access.api.ConsentAuthorization;

@Service
public class HelpCenterService implements com.raxim.myscoutee.domain.help.api.HelpCenter {
    private static final String DOCUMENT_HELP = "help";
    private static final String DOCUMENT_PRIVACY = "privacy";
    private static final String DOCUMENT_TERMS = "terms";
    private static final String DOCUMENT_EXPLANATION = "explanation";
    private static final String DEFAULT_LANG = "en";
    private final HelpCenterRevisionsRepository revisionsRepository;
    private final HelpCenterGuideFieldsRepository guideFieldsRepository;
    private final AccountConsents accountConsents;
    private final ConsentAuthorization accessControlService;

    public HelpCenterService(HelpCenterRevisionsRepository revisionsRepository,
            HelpCenterGuideFieldsRepository guideFieldsRepository,
            AccountConsents accountConsents, ConsentAuthorization accessControlService) {
        this.revisionsRepository = revisionsRepository;
        this.guideFieldsRepository = guideFieldsRepository;
        this.accountConsents = accountConsents;
        this.accessControlService = accessControlService;
    }

    public HelpCenterStateDto activeState() {
        return activeState(DOCUMENT_HELP);
    }

    public HelpCenterStateDto activeState(String documentType) {
        return activeState(documentType, DEFAULT_LANG);
    }

    public HelpCenterStateDto activeState(String documentType, String lang) {
        return activeState(documentType, lang, DEFAULT_LANG);
    }

    public HelpCenterStateDto activeState(String documentType, String lang, String acceptLanguage) {
        return activeState(documentType, lang, acceptLanguage, null);
    }

    public HelpCenterStateDto activeState(String documentType, String lang, String acceptLanguage, String contextKey) {
        String kind = documentKind(documentType);
        String language = requestContentLang(lang, acceptLanguage);
        String context = contextKey(kind, contextKey, false);
        if (DOCUMENT_EXPLANATION.equals(kind) && !StringUtils.hasText(context)) {
            return new HelpCenterStateDto(null, List.of(), List.of(), List.of(), List.of());
        }
        HelpCenterRevision active = DOCUMENT_EXPLANATION.equals(kind)
                ? revisionsRepository.findFirstByBaseGroupIdAndDocumentTypeAndLangAndContextKeyAndActiveTrueOrderByVersionDesc(
                        null, kind, language, context).orElse(null)
                : revisionsForKind(
                    revisionsRepository.findByBaseGroupIdOrderByVersionDesc(null),
                    kind,
                    language,
                    context)
                .stream()
                .filter(revision -> Boolean.TRUE.equals(revision.getActive()))
                .max(Comparator.comparing(HelpCenterRevision::getVersion, Comparator.nullsLast(Integer::compareTo)))
                .orElse(null);
        return new HelpCenterStateDto(
                active == null ? null : toRevisionDto(active),
                active == null ? List.of() : List.of(toRevisionDto(active)),
                List.of(),
                List.of(),
                guideFields(kind, context));
    }

    public PrivacyConsentDto privacyConsent(String userId, String revisionId, Integer revisionVersion) {
        String normalizedUserId = normalize(userId);
        String normalizedRevisionId = normalize(revisionId);
        if (!StringUtils.hasText(normalizedUserId) || !StringUtils.hasText(normalizedRevisionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User and privacy revision are required.");
        }
        return accountConsents.forRevisionOrLatest(normalizedUserId, normalizedRevisionId)
                .map(this::toPrivacyConsentDto)
                .orElse(null);
    }

    public PrivacyConsentDto savePrivacyConsent(PrivacyConsentSaveRequestDto request) {
        String userId = normalize(request == null ? null : request.userId());
        String revisionId = normalize(request == null ? null : request.revisionId());
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(revisionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User and privacy revision are required.");
        }
        if (!accessControlService.canCurrentActorSubmitPrivacyConsent(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user is not allowed to submit privacy consent for this user.");
        }

        HelpCenterRevision revision = revisionsRepository.findById(revisionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Privacy revision not found."));
        if (!DOCUMENT_PRIVACY.equals(revisionKind(revision))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Privacy revision not found.");
        }

        return toPrivacyConsentDto(accountConsents.record(userId, revisionId,
                Math.max(1, revision.getVersion() == null ? 1 : revision.getVersion()),
                normalizeOptionalConsentSectionIds(request == null ? null : request.approvedOptionalSectionIds(), revision),
                privacyConsentSource(request == null ? null : request.source())));
    }

    private List<HelpCenterGuideFieldDto> guideFields(String kind, String contextKey) {
        if (!DOCUMENT_EXPLANATION.equals(kind) || !StringUtils.hasText(contextKey)) {
            return List.of();
        }
        return guideFieldsRepository.findAllByScreenKeyOrderByOrderAsc(contextKey).stream()
                .map(this::toGuideFieldDto)
                .toList();
    }

    private HelpCenterGuideFieldDto toGuideFieldDto(HelpCenterGuideField field) {
        return new HelpCenterGuideFieldDto(
                field.getId(),
                field.getScreenKey(),
                field.getGroup(),
                field.getI18nKey(),
                field.getOrder());
    }

    private HelpCenterRevisionDto toRevisionDto(HelpCenterRevision revision) {
        String kind = revisionKind(revision);
        String language = revisionLang(revision);
        return new HelpCenterRevisionDto(
                revision.getId(),
                kind,
                revisionContext(revision),
                language,
                languageLabel(language),
                revision.getVersion(),
                text(revision.getTitle(), defaultTitle(kind, revision.getVersion(), language)),
                normalize(revision.getSummary()),
                normalize(revision.getDescription()),
                headerColor(revision.getHeaderColor()),
                DOCUMENT_EXPLANATION.equals(kind) && "tour".equals(revision.getPresentation()) ? "tour" : "document",
                revision.getSections() == null
                        ? List.of()
                        : revision.getSections().stream().map(section -> toSectionDto(section, kind)).toList(),
                Boolean.TRUE.equals(revision.getActive()),
                normalize(revision.getCreatedDate()),
                normalize(revision.getCreatedUser()),
                normalize(revision.getUpdatedDate()),
                normalize(revision.getUpdatedUser()),
                revision.getIsSystem());
    }

    private HelpCenterSectionDto toSectionDto(HelpCenterSection section, String kind) {
        return new HelpCenterSectionDto(
                normalize(section.getId()),
                text(section.getIcon(), defaultSectionIcon(kind)),
                text(section.getTitle(), documentLabel(kind) + " section"),
                normalize(section.getBlurb()),
                normalize(section.getContentHtml()),
                normalizeImageUrls(section.getImageUrls()),
                normalizePanelSpan(section.getPanelSpan(), section.getId()),
                DOCUMENT_PRIVACY.equals(kind) && Boolean.TRUE.equals(section.getOptional()),
                DOCUMENT_EXPLANATION.equals(kind) ? normalize(section.getGuideStepId()) : null);
    }

    private String normalizePanelSpan(String panelSpan, String sectionId) {
        String normalized = normalize(panelSpan).toLowerCase(Locale.ROOT);
        if ("span-1".equals(normalized) || "compact".equals(normalized) || "single".equals(normalized) || "one".equals(normalized) || "1".equals(normalized)) {
            return "span-1";
        }
        if ("span-2".equals(normalized) || "wide".equals(normalized) || "double".equals(normalized) || "two".equals(normalized) || "2".equals(normalized)) {
            return "span-2";
        }
        if ("span-3".equals(normalized) || "full".equals(normalized) || "row".equals(normalized) || "all".equals(normalized) || "3".equals(normalized)) {
            return "span-3";
        }
        return defaultPanelSpan(sectionId);
    }

    private String defaultPanelSpan(String sectionId) {
        String id = normalize(sectionId);
        if ("affinity-network".equals(id)
                || "activity-event-entry".equals(id)
                || "activity-event-actions".equals(id)
                || "activity-event-hosting".equals(id)
                || "activity-chat-message-window".equals(id)
                || "contacts-list".equals(id)
                || "contacts-card".equals(id)
                || "contacts-private-methods".equals(id)
                || "assets-entry".equals(id)
                || "assets-card".equals(id)
                || "assets-editor".equals(id)
                || "assets-requests".equals(id)
                || "assets-scope".equals(id)
                || "event-editor-main".equals(id)
                || "event-editor-subevents".equals(id)) {
            return "span-2";
        }
        return "span-1";
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String imageUrl : imageUrls) {
            String value = normalize(imageUrl);
            if (!StringUtils.hasText(value) || !seen.add(value)) {
                continue;
            }
            normalized.add(value);
            if (normalized.size() >= 8) {
                break;
            }
        }
        return normalized;
    }

    private PrivacyConsentDto toPrivacyConsentDto(Consent consent) {
        return new PrivacyConsentDto(
                normalize(consent.id()),
                normalize(consent.userId()),
                normalize(consent.revisionId()),
                Math.max(1, consent.revisionVersion() == null ? 1 : consent.revisionVersion()),
                consent.approvedOptionalSectionIds() == null
                        ? List.of()
                        : consent.approvedOptionalSectionIds().stream()
                                .map(this::normalize)
                                .filter(StringUtils::hasText)
                                .distinct()
                                .sorted()
                                .toList(),
                normalize(consent.acceptedAtIso()),
                normalize(consent.updatedAtIso()),
                privacyConsentSource(consent.source()));
    }

    private List<String> normalizeOptionalConsentSectionIds(List<String> sectionIds, HelpCenterRevision revision) {
        Set<String> optionalSectionIds = new LinkedHashSet<>();
        if (revision.getSections() != null) {
            for (HelpCenterSection section : revision.getSections()) {
                String sectionId = normalize(section == null ? null : section.getId());
                if (StringUtils.hasText(sectionId) && section != null && Boolean.TRUE.equals(section.getOptional())) {
                    optionalSectionIds.add(sectionId);
                }
            }
        }
        if (sectionIds == null || sectionIds.isEmpty() || optionalSectionIds.isEmpty()) {
            return List.of();
        }
        Set<String> approvedIds = new LinkedHashSet<>();
        for (String sectionId : sectionIds) {
            String normalizedSectionId = normalize(sectionId);
            if (optionalSectionIds.contains(normalizedSectionId)) {
                approvedIds.add(normalizedSectionId);
            }
        }
        return new ArrayList<>(approvedIds);
    }

    private List<HelpCenterRevision> revisionsForKind(List<HelpCenterRevision> revisions, String kind, String lang, String contextKey) {
        String language = contentLang(lang);
        String context = contextKey(kind, contextKey, false);
        boolean filterByContext = DOCUMENT_EXPLANATION.equals(kind) && StringUtils.hasText(context);
        return revisions.stream()
                .filter(revision -> kind.equals(revisionKind(revision)))
                .filter(revision -> language.equals(revisionLang(revision)))
                .filter(revision -> !filterByContext || context.equals(revisionContext(revision)))
                .peek(revision -> revision.setDocumentType(kind))
                .peek(revision -> {
                    revision.setLang(language);
                    revision.setContextKey(revisionContext(revision));
                    revision.setLanguageLabel(languageLabel(language));
                })
                .toList();
    }

    private String revisionContext(HelpCenterRevision revision) {
        return contextKey(revisionKind(revision), revision == null ? null : revision.getContextKey(), false);
    }

    private String contextKey(String kind, String contextKey, boolean required) {
        if (!DOCUMENT_EXPLANATION.equals(kind)) {
            return "";
        }
        String normalized = normalize(contextKey);
        if (!StringUtils.hasText(normalized) && !required) {
            return "";
        }
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explanation context is required.");
        }
        if (required && !guideFieldsRepository.existsByScreenKey(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown explanation context.");
        }
        return normalized;
    }

    private String revisionKind(HelpCenterRevision revision) {
        return documentKind(revision == null ? null : revision.getDocumentType());
    }

    private String revisionLang(HelpCenterRevision revision) {
        return contentLang(revision == null ? null : revision.getLang());
    }

    private String documentKind(String documentType) {
        String normalized = normalize(documentType).toLowerCase(Locale.ROOT);
        if (DOCUMENT_PRIVACY.equals(normalized)) {
            return DOCUMENT_PRIVACY;
        }
        if (DOCUMENT_TERMS.equals(normalized)) {
            return DOCUMENT_TERMS;
        }
        if (DOCUMENT_EXPLANATION.equals(normalized)) {
            return DOCUMENT_EXPLANATION;
        }
        return DOCUMENT_HELP;
    }

    private String defaultTitle(String kind, Integer version, String lang) {
        if (DOCUMENT_PRIVACY.equals(kind)) {
            return "Privacy revision v" + version;
        }
        if (DOCUMENT_TERMS.equals(kind)) {
            return "Terms revision v" + version;
        }
        if (DOCUMENT_EXPLANATION.equals(kind)) {
            return "Explanation revision v" + version;
        }
        return "Help revision v" + version;
    }

    private String contentLang(String value) {
        return text(supportedContentLang(value), DEFAULT_LANG);
    }

    private String supportedContentLang(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex > 0) {
            normalized = normalized.substring(0, semicolonIndex);
        }
        int dashIndex = normalized.indexOf('-');
        if (dashIndex > 0) {
            normalized = normalized.substring(0, dashIndex);
        }
        return "hu".equals(normalized) || DEFAULT_LANG.equals(normalized) ? normalized : "";
    }

    private String requestContentLang(String explicitLang, String acceptLanguage) {
        if (StringUtils.hasText(normalize(explicitLang))) {
            return contentLang(explicitLang);
        }
        for (String candidate : normalize(acceptLanguage).split(",")) {
            String supportedLang = supportedContentLang(candidate);
            if (StringUtils.hasText(supportedLang)) {
                return supportedLang;
            }
        }
        return DEFAULT_LANG;
    }

    private String languageLabel(String lang) {
        return "hu".equals(contentLang(lang)) ? "Magyar" : "English";
    }

    private String headerColor(String value) {
        return switch (normalize(value)) {
            case "blue", "green", "rose", "violet", "slate" -> normalize(value);
            default -> "amber";
        };
    }

    private String defaultSectionIcon(String kind) {
        if (DOCUMENT_PRIVACY.equals(kind)) {
            return "policy";
        }
        if (DOCUMENT_TERMS.equals(kind)) {
            return "rule";
        }
        if (DOCUMENT_EXPLANATION.equals(kind)) {
            return "tips_and_updates";
        }
        return "help_outline";
    }

    private String documentLabel(String kind) {
        if (DOCUMENT_PRIVACY.equals(kind)) {
            return "Privacy";
        }
        if (DOCUMENT_TERMS.equals(kind)) {
            return "Terms";
        }
        if (DOCUMENT_EXPLANATION.equals(kind)) {
            return "Explanation";
        }
        return "Help";
    }

    private String text(String value, String fallback) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String privacyConsentSource(String value) {
        return "entry".equals(normalize(value)) ? "entry" : "settings";
    }
}
