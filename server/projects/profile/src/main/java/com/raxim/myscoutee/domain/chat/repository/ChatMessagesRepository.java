package com.raxim.myscoutee.domain.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.raxim.myscoutee.domain.chat.document.ChatMessage;

public interface ChatMessagesRepository extends MongoRepository<ChatMessage, String> {
    @Query("{ 'chatId': ?0, 'senderAvatar.id': ?1, 'deletedAtIso': { $in: [null, ''] }, 'attachments.type': ?2 }")
    List<ChatMessage> findActiveSharesBySender(String chatId, String userId, String kind);

    @Query(value = "{ 'chatId': ?0 }", sort = "{ 'sentAtIso': -1 }")
    List<ChatMessage> findByChatIdOrderBySentAtIsoDesc(String chatId, Pageable pageable);

    long countByChatId(String chatId);

    long countByChatIdAndSentAtIsoGreaterThan(String chatId, String sentAtIso);

    @Query("{ 'chatId': ?0, 'id': ?1 }")
    Optional<ChatMessage> findByChatIdAndMessageId(String chatId, String messageId);

    @Query("{ 'chatId': ?0, 'id': { $in: ?1 } }")
    List<ChatMessage> findByChatIdAndMessageIds(String chatId, java.util.Collection<String> messageIds);

    List<ChatMessage> findByMessageAttachmentKeys(String attachmentKey);

    List<ChatMessage> findByMessageSenderUserIds(String userId);

    List<ChatMessage> findByMessageReadByUserIds(String userId);

    List<ChatMessage> findByMessageReactionUserIds(String userId);

    List<ChatMessage> findByPollVoterUserIds(String userId);

    default List<ChatMessage> findByMessageAttachmentTypeAndEntityId(String type, String entityId) {
        return findByMessageAttachmentKeys(ChatMessage.messageAttachmentKey(type, entityId));
    }

    default List<ChatMessage> findByMessageSenderUserId(String userId) {
        return findByMessageSenderUserIds(normalize(userId));
    }

    default List<ChatMessage> findByMessageReadByUserId(String userId) {
        return findByMessageReadByUserIds(normalize(userId));
    }

    default List<ChatMessage> findByMessageReactionUserId(String userId) {
        return findByMessageReactionUserIds(normalize(userId));
    }

    default List<ChatMessage> findByPollVoterUserId(String userId) {
        return findByPollVoterUserIds(normalize(userId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
