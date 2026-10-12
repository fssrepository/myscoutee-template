package com.raxim.myscoutee.domain.accounts.service;

import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.accounts.api.ProfileStatusObserver;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

/** Status and pending work share the account document's atomic save. Installed
 * observers complete before support messages and the guarded work-marker removal. */
@Service
public class ProfileStatusWork {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private com.raxim.myscoutee.domain.chat.api.SupportMessages supportMessages;
    private final UsersRepository users;
    private final MongoTemplate mongo;
    private final List<ProfileStatusObserver> observers;
    private final ScheduledMongoSessionRunner databases;
    public ProfileStatusWork(UsersRepository users, MongoTemplate mongo, List<ProfileStatusObserver> observers,
            ScheduledMongoSessionRunner databases) {
        this.users = users; this.mongo = mongo; this.observers = List.copyOf(observers); this.databases = databases;
    }
    public Users save(Users user) {
        user.setProfileStatusWork(UUID.randomUUID().toString());
        Users saved = users.save(user);
        deliver(saved);
        return saved;
    }
    @jakarta.annotation.PostConstruct
    public void ensureIndex() {
        databases.runAcrossConfiguredDatabases(() -> mongo.indexOps(Users.class).ensureIndex(
                new org.springframework.data.mongodb.core.index.Index().on("profileStatusWork",
                        org.springframework.data.domain.Sort.Direction.ASC).sparse()));
    }
    @org.springframework.beans.factory.annotation.Autowired
    void watchPendingWork(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes,
            @org.springframework.beans.factory.annotation.Value("${users.profile-status-recovery-delay-ms:10000}") long retryMs) {
        changes.watch("ProfileStatusWork", java.util.Set.of("users"), java.util.Set.of("profileStatusWork"), () -> {
            recoverInCurrentDatabase();
            return mongo.exists(Query.query(Criteria.where("profileStatusWork").exists(true).ne(null)), Users.class)
                    ? java.time.Instant.now().plusMillis(Math.max(1, retryMs)) : null;
        });
    }

    public void recover() {
        databases.runAcrossConfiguredDatabases(this::recoverInCurrentDatabase);
    }

    private void recoverInCurrentDatabase() {
        mongo.find(Query.query(Criteria.where("profileStatusWork")
                .exists(true).ne(null)).limit(20), Users.class).forEach(this::deliver);
    }
    private void deliver(Users user) {
        if (user == null || user.getProfileStatusWork() == null) return;
        try {
            JobGroupContext.run(user.getWorkspaceGroupId(), () -> {
                for (var observer : observers) observer.onProfileStatusChanged(user.getUserId());
            });
            if (!user.getProfileSupportWork().isEmpty() && supportMessages == null) {
                throw new IllegalStateException("Support work requires its delivery owner");
            }
            for (var message : user.getProfileSupportWork()) {
                JobGroupContext.run(user.getWorkspaceGroupId(), () -> supportMessages.sendAdminSupportMessage(
                        user.getUserId(), message.adminUserId(), message.adminName(), message.adminInitials(),
                        message.text(), "blocked", message.id()));
                mongo.updateFirst(Query.query(Criteria.where("userId").is(user.getUserId())),
                        new Update().pull("profileSupportWork", new org.bson.Document("id", message.id())), Users.class);
            }
            mongo.updateFirst(Query.query(Criteria.where("userId").is(user.getUserId())
                            .and("profileStatusWork").is(user.getProfileStatusWork())),
                    new Update().unset("profileStatusWork"), Users.class);
        } catch (RuntimeException pending) {
            org.slf4j.LoggerFactory.getLogger(ProfileStatusWork.class).warn(
                    "Profile status propagation remains pending ({})", pending.getClass().getSimpleName());
        }
    }
}
