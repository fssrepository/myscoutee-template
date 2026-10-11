package com.raxim.myscoutee.domain.accounts.service;

import java.util.List;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import com.raxim.myscoutee.domain.accounts.api.AccountMediaVisibility;

@Service
public class AccountMediaVisibilityService implements AccountMediaVisibility {
    private final MongoTemplate mongo;
    public AccountMediaVisibilityService(MongoTemplate mongo) { this.mongo = mongo; }
    @Override public Profile profile(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        query.fields().include("profileStatus").include("accountUserId").include("workspaceGroupId");
        Document value = mongo.findOne(query, Document.class, "users");
        return value == null ? null : new Profile(value.getString("profileStatus"), value.getString("accountUserId"), value.getString("workspaceGroupId"));
    }
}
