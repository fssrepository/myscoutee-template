package com.raxim.myscoutee.domain.help.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.raxim.myscoutee.domain.access.api.ConsentAuthorization;
import com.raxim.myscoutee.domain.accounts.api.AccountConsents;
import com.raxim.myscoutee.domain.help.document.HelpCenterRevision;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.PrivacyConsentSaveRequestDto;
import com.raxim.myscoutee.domain.help.repository.HelpCenterGuideFieldsRepository;
import com.raxim.myscoutee.domain.help.repository.HelpCenterRevisionsRepository;

@Tag("standard")
class HelpCenterServiceTest {
    private HelpCenterRevisionsRepository revisions;
    private HelpCenterGuideFieldsRepository fields;
    private AccountConsents consents;
    private ConsentAuthorization authorization;
    private HelpCenterService service;

    @BeforeEach void setup() {
        revisions = mock(HelpCenterRevisionsRepository.class);
        fields = mock(HelpCenterGuideFieldsRepository.class);
        consents = mock(AccountConsents.class);
        authorization = mock(ConsentAuthorization.class);
        service = new HelpCenterService(revisions, fields, consents, authorization);
    }

    @Test void publicDocumentSelectsActiveRevisionInRequestedLanguage() {
        var old = revision("privacy-en-v1", "privacy", "en", 1, true);
        var current = revision("privacy-en-v2", "privacy", "en", 2, true);
        var draft = revision("privacy-en-v3", "privacy", "en", 3, false);
        var hu = revision("privacy-hu-v2", "privacy", "hu", 2, true);
        when(revisions.findByBaseGroupIdOrderByVersionDesc(null)).thenReturn(List.of(old, current, draft, hu));
        assertThat(service.activeState("privacy", "en", "hu", null).activeRevision().id()).isEqualTo(current.getId());
        assertThat(service.activeState("privacy", null, "hu-HU,en", null).activeRevision().id()).isEqualTo(hu.getId());
        verify(revisions, times(2)).findByBaseGroupIdOrderByVersionDesc(null);
        verifyNoMoreInteractions(revisions);
        verifyNoInteractions(fields, consents);
    }

    @Test void guideUsesNativeScreenContextLookupWithoutLoadingOtherDocuments() {
        var guide = revision("member-guide-en", "explanation", "en", 1, true);
        guide.setContextKey("member");
        when(revisions.findFirstByBaseGroupIdAndDocumentTypeAndLangAndContextKeyAndActiveTrueOrderByVersionDesc(
                null, "explanation", "en", "member")).thenReturn(Optional.of(guide));
        assertThat(service.activeState("explanation", "en", "en", "member").activeRevision().id()).isEqualTo(guide.getId());
        verify(revisions).findFirstByBaseGroupIdAndDocumentTypeAndLangAndContextKeyAndActiveTrueOrderByVersionDesc(
                null, "explanation", "en", "member");
        verifyNoMoreInteractions(revisions);
    }

    @Test void consentPersistsAuthoritativeVersionAndOnlyKnownOptionalSections() {
        var privacy = revision("privacy-en", "privacy", "en", 2, true);
        var optional = new HelpCenterRevision.HelpCenterSection();
        optional.setId("optional"); optional.setOptional(true);
        var required = new HelpCenterRevision.HelpCenterSection();
        required.setId("required"); required.setOptional(false);
        privacy.setSections(List.of(optional, required));
        when(authorization.canCurrentActorSubmitPrivacyConsent("alice")).thenReturn(true);
        when(revisions.findById(privacy.getId())).thenReturn(Optional.of(privacy));
        when(consents.record("alice", privacy.getId(), 2, List.of("optional"), "entry"))
                .thenReturn(new AccountConsents.Consent("consent-1", "alice", privacy.getId(), 2,
                        List.of("optional"), "2026-10-11T00:00:00Z", "2026-10-11T00:00:00Z", "entry"));
        service.savePrivacyConsent(new PrivacyConsentSaveRequestDto("alice", privacy.getId(), 999,
                List.of("optional", "required", "unknown", "optional"), "entry"));
        verify(consents).record("alice", privacy.getId(), 2, List.of("optional"), "entry");
    }

    @Test void unauthorizedConsentNeverReadsOrWritesAnotherPersonsData() {
        assertThatThrownBy(() -> service.savePrivacyConsent(new PrivacyConsentSaveRequestDto(
                "bob", "privacy-en", 1, List.of(), "entry"))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(revisions, consents);
    }

    private HelpCenterRevision revision(String id, String type, String lang, int version, boolean active) {
        var revision = new HelpCenterRevision();
        revision.setId(id); revision.setDocumentType(type); revision.setLang(lang);
        revision.setVersion(version); revision.setActive(active); revision.setSections(List.of());
        return revision;
    }
}
