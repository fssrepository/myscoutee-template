package com.raxim.myscoutee.domain.notifications.service;

import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.raxim.myscoutee.domain.notifications.api.NotificationPrivacy;

@Service
public class NotificationPrivacyService implements NotificationPrivacy {
    private final MongoTemplate mongo;
    public NotificationPrivacyService(MongoTemplate mongo) { this.mongo = mongo; }

    @Override
    public void purgeDeliveryData(String userId) {
        if (StringUtils.hasText(userId)) {
            mongo.remove(Query.query(new Criteria().orOperator(
                    Criteria.where("recipientUserIds").is(userId), Criteria.where("createdUser").is(userId),
                    Criteria.where("updatedUser").is(userId))), "notificationOutbox");
        }
        mongo.remove(Query.query(Criteria.where("recipientUserId").is(userId)), "userNotifications");
        Update senderRedaction = new Update().set("senderUserId", "deleted-user")
                .set("title", "Notification from a deleted user")
                .set("message", "This notification was sent by a deleted user.").set("payload", new Document());
        mongo.updateMulti(Query.query(new Criteria().orOperator(
                Criteria.where("senderUserId").is(userId), Criteria.where("payload.senderUserId").is(userId),
                Criteria.where("payload.actorUserId").is(userId))), senderRedaction, "userNotifications");
        anonymizeAuthors("userNotifications", userId);
    }

    @Override
    public void anonymizeRuleAuthors(String userId) { anonymizeAuthors("notificationRuleConfigs", userId); }

    private void anonymizeAuthors(String collection, String userId) {
        if (!StringUtils.hasText(userId)) return;
        for (String field : List.of("createdUser", "updatedUser")) {
            mongo.updateMulti(Query.query(Criteria.where(field).is(userId)),
                    new Update().set(field, "deleted-user"), collection);
        }
    }
}
