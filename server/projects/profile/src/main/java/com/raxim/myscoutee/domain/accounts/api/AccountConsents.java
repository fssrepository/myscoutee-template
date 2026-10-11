package com.raxim.myscoutee.domain.accounts.api;

import java.util.List;
import java.util.Optional;

public interface AccountConsents {
    Optional<Consent> forRevisionOrLatest(String userId, String revisionId);
    Consent record(String userId, String revisionId, int revisionVersion, List<String> optionalSectionIds, String source);
    record Consent(String id, String userId, String revisionId, Integer revisionVersion,
            List<String> approvedOptionalSectionIds, String acceptedAtIso, String updatedAtIso, String source) {}
    Optional<Consent> latest(String userId);
}
