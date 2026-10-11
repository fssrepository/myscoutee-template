package com.raxim.myscoutee.domain.accounts.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import com.raxim.myscoutee.domain.accounts.document.UserFriendship;

public interface UserFriendshipsRepository extends MongoRepository<UserFriendship, String> {
    @Query("{'$or': [{'leftUserId': {'$in': ?0}}, {'rightUserId': {'$in': ?0}}]}")
    List<UserFriendship> findRelevantByUserIds(Collection<String> userIds);
}
