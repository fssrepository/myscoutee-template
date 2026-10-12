package com.raxim.myscoutee.domain.help.service;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.raxim.myscoutee.domain.help.api.HelpPrivacy;

@Service
public class HelpPrivacyService implements HelpPrivacy {
    private final MongoTemplate mongo;
    public HelpPrivacyService(MongoTemplate mongo) { this.mongo = mongo; }
    @Override
    public void anonymizeAuthors(String userId) {
        if (!StringUtils.hasText(userId)) return;
        mongo.updateMulti(Query.query(Criteria.where("actorUserId").is(userId)),
                new Update().set("actorUserId", "deleted-user"), "helpCenterAudit");
        for (String field : java.util.List.of("createdUser", "updatedUser")) {
            mongo.updateMulti(Query.query(Criteria.where(field).is(userId)),
                    new Update().set(field, "deleted-user"), "helpCenterRevisions");
        }
    }
}
