package com.raxim.myscoutee.domain.media.config;

import java.util.List;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Component;
import com.mongodb.client.model.IndexOptions;
import jakarta.annotation.PostConstruct;

@Component
public class MediaDeletionIndexInitializer {
    private final List<MongoDatabaseFactory> databases;

    public MediaDeletionIndexInitializer(
            @Qualifier("realMongoDatabaseFactory") MongoDatabaseFactory real,
            @Qualifier("demoMongoDatabaseFactory") MongoDatabaseFactory demo) {
        databases = List.of(real, demo);
    }

    @PostConstruct
    public void ensureIndexes() {
        for (var factory : databases) {
            var database = factory.getMongoDatabase();
            var collection = database.getCollection("mediaDeletions");
            // Retire the pre-status development index; final failures must be excluded by the due index.
            if (database.listCollectionNames().into(new java.util.ArrayList<>()).contains("mediaDeletions")) {
                for (var index : collection.listIndexes()) {
                    if ("media_deletion_due".equals(index.getString("name"))
                            && new Document("baseGroupId", 1).append("nextAttemptAt", 1).append("_id", 1).equals(index.get("key")))
                        collection.dropIndex("media_deletion_due");
                }
            }
            collection.createIndex(new Document("baseGroupId", 1).append("status", 1).append("nextAttemptAt", 1).append("_id", 1),
                    new IndexOptions().name("media_deletion_due_v1"));
        }
    }
}
