package com.raxim.myscoutee.domain.integrations.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.raxim.myscoutee.domain.integrations.document.McpEventSubscription;

public interface McpEventSubscriptionsRepository extends MongoRepository<McpEventSubscription, String> {
    boolean existsByOwnerUserIdAndNameAndEnabledTrueAndExpiresAtAfter(String owner, String name, Instant now);
    List<McpEventSubscription> findByOwnerUserIdAndNameAndResourceIdAndEnabledTrueAndExpiresAtAfter(
            String owner, String name, String resourceId, Instant now);
    long countByOwnerUserIdAndEnabledTrueAndExpiresAtAfter(String owner, Instant now);
    Optional<McpEventSubscription> findFirstByOwnerUserIdAndTokenIdAndClientIdAndCallbackUrlAndSecretHashAndVerifiedUntilAfter(
            String owner, String tokenId, String clientId, String callbackUrl, String secretHash, Instant now);
}
