package com.raxim.myscoutee.domain.integrations.repository;

import java.time.Instant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import com.raxim.myscoutee.domain.integrations.document.McpAuthorization;

@Repository
public class McpAuthorizationRepository {
    private final MongoTemplate mongo;
    public McpAuthorizationRepository(MongoTemplate mongo) { this.mongo = mongo; }
    public void save(McpAuthorization value) { mongo.insert(value); }
    public McpAuthorization activeGrant(String id, String owner, String clientId, String resource) {
        if (id == null) return null;
        return mongo.findOne(Query.query(active().and("_id").is(id).and("kind").is("grant")
                .and("ownerUserId").is(owner).and("clientId").is(clientId)
                .and("resource").is(resource)), McpAuthorization.class);
    }
    private Criteria active() {
        return Criteria.where("revoked").ne(true).and("expiresAt").gt(Instant.now());
    }
    public McpAuthorization consumeCode(String hash, String clientId, String redirect, String resource, String challenge) {
        return mongo.findAndRemove(Query.query(active().and("_id").is(hash).and("kind").is("code")
                .and("clientId").is(clientId).and("redirectUri").is(redirect)
                .and("resource").is(resource).and("challenge").is(challenge)), McpAuthorization.class);
    }
    public McpAuthorization access(String hash, String resource) {
        return mongo.findOne(Query.query(active().and("kind").is("grant").and("accessHash").is(hash)
                .and("accessExpiresAt").gt(Instant.now()).and("resource").is(resource)), McpAuthorization.class);
    }
    public McpAuthorization rotate(String hash, String clientId, String resource, String access, String refresh) {
        return mongo.findAndModify(Query.query(active().and("kind").is("grant").and("refreshHash").is(hash)
                        .and("clientId").is(clientId).and("resource").is(resource).and("usedRefreshHashes.4095").exists(false)),
                new Update().set("accessHash", access).set("refreshHash", refresh)
                        .set("accessExpiresAt", Instant.now().plusSeconds(3600)).addToSet("usedRefreshHashes", hash),
                FindAndModifyOptions.options().returnNew(true), McpAuthorization.class);
    }
    public void revokeReplayedRefresh(String hash, String clientId) {
        mongo.updateMulti(Query.query(Criteria.where("kind").is("grant").and("clientId").is(clientId)
                .and("usedRefreshHashes").is(hash)), new Update().set("revoked", true), McpAuthorization.class);
    }
    public void revokeClient(String clientId) {
        mongo.updateMulti(Query.query(new Criteria().orOperator(Criteria.where("tokenId").is(clientId),
                        Criteria.where("clientId").is(clientId))),
                new Update().set("revoked", true), McpAuthorization.class);
    }
}
