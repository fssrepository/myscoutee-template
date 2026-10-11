package com.raxim.myscoutee.domain.accounts.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import com.raxim.myscoutee.domain.accounts.document.Users;

public interface UsersRepository extends MongoRepository<Users, String> {
    Optional<Users> findByUserId(String userId);
    Optional<Users> findFirstByEmail(String email);
    Optional<Users> findFirstByFirebaseUid(String firebaseUid);
    Optional<Users> findFirstByGoogleId(String googleId);
    Optional<Users> findFirstByFacebookId(String facebookId);
    List<Users> findByUserIdIn(Collection<String> userIds);
    List<Users> findByProfileStatusAndDeletedAtIsoLessThanEqual(String profileStatus, String deletedAtIso);

    @Query(value = "{}", sort = "{ 'name': 1, 'userId': 1 }", collation = "{ 'locale': 'en', 'strength': 2 }")
    List<Users> findAllByOrderByNameAscUserIdAsc();
}
