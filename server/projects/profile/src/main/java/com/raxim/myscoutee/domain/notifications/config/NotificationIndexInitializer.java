package com.raxim.myscoutee.domain.notifications.config;

import java.util.List;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Component;
import com.mongodb.client.model.IndexOptions;
import jakarta.annotation.PostConstruct;

/** Owns the existing group-scoped notification indexes; names and migration order are unchanged. */
@Component
public class NotificationIndexInitializer {
    private final List<MongoDatabaseFactory> databases;

    public NotificationIndexInitializer(@Qualifier("realMongoDatabaseFactory") MongoDatabaseFactory real,
            @Qualifier("demoMongoDatabaseFactory") MongoDatabaseFactory demo) {
        databases = List.of(real, demo);
    }

    @PostConstruct
    public void initialize() {
        for (var factory : databases) {
            var collection = factory.getMongoDatabase().getCollection("notificationRuleConfigs");
            collection.createIndex(new Document("baseGroupId", 1).append("ruleKey", 1),
                    new IndexOptions().name("notification_rule_group_key").unique(true));
            for (var index : collection.listIndexes()) {
                if ("notification_rule_config_rule_key".equals(index.getString("name"))) {
                    collection.dropIndex("notification_rule_config_rule_key");
                    break;
                }
            }
        }
    }
}
