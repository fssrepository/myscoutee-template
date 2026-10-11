package com.raxim.myscoutee.domain.accounts.service;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.util.StringUtils;
import com.raxim.myscoutee.domain.accounts.api.ChatCounters;
import com.raxim.myscoutee.domain.accounts.document.Users;
@Service
public class AccountChatCounterService implements ChatCounters {
    private final MongoTemplate mongo;
    public AccountChatCounterService(MongoTemplate mongo) { this.mongo = mongo; }
    public void increment(Change change) {
        mongo.updateFirst(Query.query(Criteria.where("userId").is(change.ownerUserId())), update(change), Users.class);
    }
    public void increment(Collection<Change> changes) {
        if (changes.isEmpty()) return;
        var bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, Users.class);
        for (var change : changes) bulk.updateOne(Query.query(Criteria.where("userId").is(change.ownerUserId())), update(change));
        bulk.execute();
    }
    private Update update(Change change) {
        var update = new Update().inc("activities.chats", change.delta()).inc("activities.chat.all", change.delta());
        if (StringUtils.hasText(change.context())) update.inc("activities.chat." + change.context(), change.delta());
        return update;
    }
}
