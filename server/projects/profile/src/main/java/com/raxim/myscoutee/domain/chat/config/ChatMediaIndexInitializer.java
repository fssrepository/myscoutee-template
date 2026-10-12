package com.raxim.myscoutee.domain.chat.config;

import java.util.List;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Component;
import com.mongodb.client.model.IndexOptions;
import jakarta.annotation.PostConstruct;

@Component
public class ChatMediaIndexInitializer {
    private final List<MongoDatabaseFactory> databases;

    public ChatMediaIndexInitializer(
            @Qualifier("realMongoDatabaseFactory") MongoDatabaseFactory real,
            @Qualifier("demoMongoDatabaseFactory") MongoDatabaseFactory demo) {
        databases = List.of(real, demo);
    }

    @PostConstruct
    public void ensureIndexes() {
        for (var factory : databases) {
            var database = factory.getMongoDatabase();
            for (String field : List.of("attachments.url", "attachments.previewUrl")) {
                database.getCollection("chatMessages").createIndex(new Document(field, 1),
                        new IndexOptions().name("media_" + field.replace('.', '_')).sparse(true));
            }
        }
    }
}
