package com.raxim.myscoutee.domain.accounts.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

@Service
public class ProfileDirectoryService implements ProfileDirectory, ProfileSummaries {
    private final UsersRepository users;
    public ProfileDirectoryService(UsersRepository users) { this.users = users; }
    @Override public Optional<Users> findByUserId(String userId) { return users.findByUserId(userId); }
    @Override public Users findFirstByUserId(String id) { return users.findByUserId(id).orElse(null); }
    @Override public List<Users> findByUserIdIn(Collection<String> userIds) { return users.findByUserIdIn(userIds); }
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongo;
    public long countByUserIdIn(Collection<String> ids) { return mongo.count(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("userId").in(ids)), Users.class); }
    public List<Users> pageByUserIdIn(Collection<String> ids, int offset, int limit) {
        return mongo.find(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("userId").in(ids))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "userId")).limit(limit).skip(offset), Users.class);
    }
    @Override public List<Users> findByProfileStatus(String status) { return users.findByProfileStatus(status); }
}
