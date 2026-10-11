package com.raxim.myscoutee.domain.notifications.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem;

public interface NotificationOutboxRepository extends MongoRepository<NotificationOutboxItem, String> {
    Optional<NotificationOutboxItem> findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
            String collapseKey,
            Collection<String> statuses,
            String createdDate);

    long countByStatus(String status);
    long countByBaseGroupIdAndStatus(String baseGroupId, String status);
}
