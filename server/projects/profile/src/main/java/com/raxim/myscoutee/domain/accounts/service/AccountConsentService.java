package com.raxim.myscoutee.domain.accounts.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.raxim.myscoutee.domain.accounts.api.AccountConsents;
import com.raxim.myscoutee.domain.accounts.document.PrivacyConsent;
import com.raxim.myscoutee.domain.accounts.repository.PrivacyConsentsRepository;

@Service
public class AccountConsentService implements AccountConsents {
    private final PrivacyConsentsRepository repository;
    public AccountConsentService(PrivacyConsentsRepository repository) { this.repository = repository; }

    @Override
    public Optional<Consent> forRevisionOrLatest(String userId, String revisionId) {
        return repository.findByUserIdAndRevisionId(userId, revisionId)
                .or(() -> repository.findTopByUserIdOrderByUpdatedAtIsoDesc(userId)).map(this::snapshot);
    }

    @Override
    public Consent record(String userId, String revisionId, int revisionVersion, List<String> optionalSectionIds, String source) {
        String nowIso = Instant.now().toString();
        PrivacyConsent consent = repository.findByUserIdAndRevisionId(userId, revisionId).orElseGet(PrivacyConsent::new);
        int currentVersion = consent.getRevisionVersion() == null ? 0 : consent.getRevisionVersion();
        if (!StringUtils.hasText(consent.getId())) consent.setId(new ObjectId().toHexString());
        if (!StringUtils.hasText(consent.getAcceptedAtIso()) || currentVersion < revisionVersion) consent.setAcceptedAtIso(nowIso);
        consent.setUserId(userId);
        consent.setRevisionId(revisionId);
        consent.setRevisionVersion(revisionVersion);
        consent.setApprovedOptionalSectionIds(optionalSectionIds);
        consent.setUpdatedAtIso(nowIso);
        consent.setSource(source);
        return snapshot(repository.save(consent));
    }

    private Consent snapshot(PrivacyConsent consent) {
        return new Consent(consent.getId(), consent.getUserId(), consent.getRevisionId(), consent.getRevisionVersion(),
                consent.getApprovedOptionalSectionIds() == null ? List.of() : java.util.Collections.unmodifiableList(
                        new java.util.ArrayList<>(consent.getApprovedOptionalSectionIds())),
                consent.getAcceptedAtIso(), consent.getUpdatedAtIso(), consent.getSource());
    }
    public Optional<Consent> latest(String userId) { return repository.findTopByUserIdOrderByUpdatedAtIsoDesc(userId).map(this::snapshot); }
}
