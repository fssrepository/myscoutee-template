package com.raxim.myscoutee.domain.chat.repository;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import com.raxim.myscoutee.domain.chat.document.ContactChatAccess;

public interface ContactChatAccessRepository extends MongoRepository<ContactChatAccess, String> {
    @Query("{ '$or': [ { 'leftUserId': ?0 }, { 'rightUserId': ?0 } ] }")
    List<ContactChatAccess> findForUser(String userId);

    default boolean isApproved(String first, String second) {
        return findById(ContactChatAccess.pairId(first, second))
                .map(value -> "approved".equals(value.status())).orElse(false);
    }
}
