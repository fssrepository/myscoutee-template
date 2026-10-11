package com.raxim.myscoutee.domain.notifications.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.notifications.document.UserNotification;

public interface UserNotificationsRepository extends MongoRepository<UserNotification, String> {
    Optional<UserNotification> findByIdAndRecipientUserId(String id, String recipientUserId);

    long deleteAllByRecipientUserId(String recipientUserId);
}
