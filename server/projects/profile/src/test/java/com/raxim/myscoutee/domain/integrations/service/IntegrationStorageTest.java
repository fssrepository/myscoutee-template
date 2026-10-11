package com.raxim.myscoutee.domain.integrations.service;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;

@Tag("embedded")
class IntegrationStorageTest {
    @Test void purgeRemovesOnlyTheDeletedOwnersCredentialsAndKeepsSharedClientRegistration() {
        try (var mongod = Mongod.instance().start(Version.Main.V6_0)) {
            var address = mongod.current().getServerAddress();
            try (var client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
                var mongo = new MongoTemplate(client, "integration-purge");
                for (String collection : List.of("integrationTokens", "mcpAuthorizations")) {
                    mongo.getCollection(collection).insertMany(List.of(
                            new Document("_id", "deleted-credential").append("ownerUserId", "deleted-owner"),
                            new Document("_id", "retained-credential").append("ownerUserId", "retained-owner")));
                }
                mongo.getCollection("mcpOAuthClients").insertOne(new Document("_id", "shared-public-client")
                        .append("expiresAt", Date.from(Instant.now().plusSeconds(86400))));
                var privacy = new IntegrationPrivacyService(mongo);
                privacy.purgeParticipantData(null); privacy.purgeParticipantData(" ");
                assertThat(mongo.getCollection("integrationTokens").countDocuments()).isEqualTo(2);
                privacy.purgeParticipantData("deleted-owner"); privacy.purgeParticipantData("deleted-owner");
                for (String collection : List.of("integrationTokens", "mcpAuthorizations")) {
                    assertThat(mongo.getCollection(collection).find().into(new ArrayList<>()))
                            .singleElement().satisfies(row -> assertThat(row.getString("ownerUserId")).isEqualTo("retained-owner"));
                }
                assertThat(mongo.getCollection("mcpOAuthClients").countDocuments()).isEqualTo(1);
            }
        }
    }
}
