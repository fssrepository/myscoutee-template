package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.raxim.myscoutee.domain.accounts.document.PrivacyConsent;
import com.raxim.myscoutee.domain.accounts.repository.PrivacyConsentsRepository;

@Tag("standard")
class AccountConsentServiceTest {
    private final PrivacyConsentsRepository repository = mock(PrivacyConsentsRepository.class);
    private final AccountConsentService service = new AccountConsentService(repository);

    @Test
    void exactRevisionAvoidsFallbackAndReturnsDetachedSections() {
        var stored = consent();
        stored.setApprovedOptionalSectionIds(new ArrayList<>(List.of("analytics")));
        when(repository.findByUserIdAndRevisionId("u", "r")).thenReturn(Optional.of(stored));
        var result = service.forRevisionOrLatest("u", "r").orElseThrow();
        stored.getApprovedOptionalSectionIds().clear();
        assertThat(result.approvedOptionalSectionIds()).containsExactly("analytics");
        verify(repository, never()).findTopByUserIdOrderByUpdatedAtIsoDesc(any());
    }

    @Test
    void missingRevisionUsesLatestSavedConsent() {
        when(repository.findByUserIdAndRevisionId("u", "missing")).thenReturn(Optional.empty());
        when(repository.findTopByUserIdOrderByUpdatedAtIsoDesc("u")).thenReturn(Optional.of(consent()));
        assertThat(service.forRevisionOrLatest("u", "missing").orElseThrow().revisionId()).isEqualTo("r");
    }

    @Test
    void sameVersionKeepsAcceptanceTimestampAndUpgradeRenewsIt() {
        var stored = consent();
        when(repository.findByUserIdAndRevisionId("u", "r")).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var unchanged = service.record("u", "r", 2, List.of("analytics"), "profile");
        assertThat(unchanged.acceptedAtIso()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(unchanged.id()).isEqualTo("saved-id");
        var upgraded = service.record("u", "r", 3, List.of(), "profile");
        assertThat(upgraded.acceptedAtIso()).isNotEqualTo(unchanged.acceptedAtIso()).isEqualTo(upgraded.updatedAtIso());
        assertThat(upgraded.revisionVersion()).isEqualTo(3);
        assertThat(upgraded.approvedOptionalSectionIds()).isEmpty();
    }

    private PrivacyConsent consent() {
        var consent = new PrivacyConsent();
        consent.setId("saved-id"); consent.setUserId("u"); consent.setRevisionId("r");
        consent.setRevisionVersion(2); consent.setAcceptedAtIso("2026-01-01T00:00:00Z");
        return consent;
    }
}
