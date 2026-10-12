package com.raxim.myscoutee.domain.accounts.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.accounts.document.PrivacyConsent;

public interface PrivacyConsentsRepository extends MongoRepository<PrivacyConsent, String> {
    Optional<PrivacyConsent> findByUserIdAndRevisionId(String userId, String revisionId);

    Optional<PrivacyConsent> findTopByUserIdOrderByUpdatedAtIsoDesc(String userId);
}
