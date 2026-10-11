package com.raxim.myscoutee.domain.integrations.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.integrations.document.IntegrationToken;

public interface IntegrationTokensRepository extends MongoRepository<IntegrationToken, String> {
    Optional<IntegrationToken> findByTokenHash(String tokenHash);

    List<IntegrationToken> findByOwnerUserIdOrderByCreatedAtIsoDesc(String ownerUserId);

    Optional<IntegrationToken> findByIdAndOwnerUserId(String id, String ownerUserId);
}
