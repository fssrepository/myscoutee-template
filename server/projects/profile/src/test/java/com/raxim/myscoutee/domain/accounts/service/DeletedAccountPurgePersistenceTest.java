package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.accounts.document.*;
import com.raxim.myscoutee.domain.accounts.repository.*;
import com.raxim.myscoutee.domain.chat.repository.*;
import com.raxim.myscoutee.domain.chat.service.ChatPrivacyService;
import com.raxim.myscoutee.domain.help.service.HelpPrivacyService;
import com.raxim.myscoutee.domain.media.api.MediaCleanup;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;
import com.raxim.myscoutee.domain.notifications.service.NotificationPrivacyService;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.domain.platform.service.JobGroupScopeService;

@Tag("embedded")
class DeletedAccountPurgePersistenceTest {
    @Test void expiryCleanupPersistsAndRetryAfterReconstructionKeepsTheOtherMember() {
        try (var mongod = Mongod.instance().start(Version.Main.V6_0)) {
            var address = mongod.current().getServerAddress();
            try (var client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
                var mongo = new MongoTemplate(client, "template-account-purge");
                var repositories = new MongoRepositoryFactory(mongo);
                var users = repositories.getRepository(UsersRepository.class);
                var jobs = repositories.getRepository(AccountPurgeJobsRepository.class);
                var chats = repositories.getRepository(ChatsRepository.class);
                var messages = repositories.getRepository(ChatMessagesRepository.class);
                users.save(member("expired", 31)); users.save(member("recent", 1));
                for (String collection : List.of("integrationTokens", "mcpAuthorizations", "mcpEventSubscriptions", "integrationWebhookDeliveries")) {
                    mongo.getCollection(collection).insertMany(List.of(
                            new Document("_id", "expired-credential").append("ownerUserId", "expired"),
                            new Document("_id", "recent-credential").append("ownerUserId", "recent")));
                }
                mongo.getCollection("mcpOAuthClients").insertOne(new Document("_id", "shared-client"));
                for (String collection : List.of("privacyConsents", "userSessionActions", "accountDeletionWarnings")) {
                    mongo.getCollection(collection).insertMany(List.of(
                            new Document("_id", "expired-record").append("userId", "expired"),
                            new Document("_id", "recent-record").append("userId", "recent")));
                }
                mongo.getCollection("userNotifications").insertMany(List.of(
                        new Document("_id", "expired-inbox").append("recipientUserId", "expired"),
                        new Document("_id", "recent-inbox").append("recipientUserId", "recent")
                                .append("senderUserId", "expired").append("message", "Private message")
                                .append("payload", new Document("senderUserId", "expired"))));
                mongo.getCollection("helpCenterRevisions").insertOne(new Document("_id", "guide")
                        .append("createdUser", "expired").append("updatedUser", "recent"));
                mongo.getCollection("chats").insertOne(new Document("_id", "chat-copy").append("id", "conversation")
                        .append("ownerUserId", "recent").append("memberIds", List.of("expired", "recent")));
                mongo.getCollection("chatMessages").insertMany(List.of(
                        message("expired-message", "expired", "Private text"),
                        message("recent-message", "recent", "Keep this text")));
                var media = mock(MediaCleanup.class);
                doThrow(new IllegalStateException("temporary object store failure")).doNothing()
                        .when(media).deletePublicObjectOrThrow("expired-avatar.webp");
                var first = worker(users, jobs, new ChatPrivacyService(chats, messages, media), media, mongo);
                first.purgeExpiredDeletedAccounts();
                assertThat(users.findByUserId("expired")).isPresent();
                var failed = jobs.findByUserId("expired").orElseThrow();
                assertThat(failed.getStatus()).isEqualTo(AccountPurgeJob.STATUS_FAILED);
                assertThat(failed.getRetryCount()).isEqualTo(1);
                failed.setNextRetryAtIso(Instant.now().minusSeconds(1).toString()); jobs.save(failed);

                // New worker/repository instances read the durable intent from MongoDB.
                var resumed = worker(repositories.getRepository(UsersRepository.class),
                        repositories.getRepository(AccountPurgeJobsRepository.class),
                        new ChatPrivacyService(chats, messages, media), media, mongo);
                resumed.purgeExpiredDeletedAccounts();
                assertThat(users.findByUserId("expired")).isEmpty();
                assertThat(users.findByUserId("recent")).isPresent();
                var completed = jobs.findById(failed.getId()).orElseThrow();
                assertThat(completed.getStatus()).isEqualTo(AccountPurgeJob.STATUS_COMPLETED);
                assertThat(completed.getUserId()).isNull();
                for (String collection : List.of("integrationTokens", "mcpAuthorizations", "mcpEventSubscriptions", "integrationWebhookDeliveries")) {
                    assertThat(mongo.getCollection(collection).countDocuments(new Document("ownerUserId", "expired"))).isZero();
                    assertThat(mongo.getCollection(collection).countDocuments(new Document("ownerUserId", "recent"))).isEqualTo(1);
                }
                assertThat(mongo.getCollection("mcpOAuthClients").countDocuments()).isEqualTo(1);
                for (String collection : List.of("privacyConsents", "userSessionActions", "accountDeletionWarnings")) {
                    assertThat(mongo.getCollection(collection).countDocuments(new Document("userId", "expired"))).isZero();
                    assertThat(mongo.getCollection(collection).countDocuments(new Document("userId", "recent"))).isEqualTo(1);
                }
                assertThat(mongo.getCollection("userNotifications").find(new Document("_id", "expired-inbox")).first()).isNull();
                var notification = mongo.getCollection("userNotifications").find(new Document("_id", "recent-inbox")).first();
                assertThat(notification.getString("senderUserId")).isEqualTo("deleted-user");
                assertThat(notification.get("payload", Document.class)).isEmpty();
                assertThat(mongo.getCollection("helpCenterRevisions").find().first().getString("createdUser"))
                        .isEqualTo("deleted-user");
                assertThat(chats.findByChatId("conversation").getFirst().getMemberIds()).containsExactly("recent");
                var removed = messages.findByChatIdAndMessageId("conversation", "expired-message").orElseThrow();
                assertThat(removed.getText()).isEmpty(); assertThat(removed.getDeletedAtIso()).isNotBlank();
                assertThat(messages.findByChatIdAndMessageId("conversation", "recent-message").orElseThrow().getText())
                        .isEqualTo("Keep this text");
                resumed.purgeExpiredDeletedAccounts();
                verify(media, times(2)).deletePublicObjectOrThrow("expired-avatar.webp");
                verify(media, never()).deletePublicObjectOrThrow("recent-avatar.webp");
            }
        }
    }

    private Users member(String id, int days) {
        var user = new Users(); user.setId(id); user.setUserId(id);
        user.setProfileStatus("deleted"); user.setStatus("D");
        user.setDeletedAtIso(Instant.now().minusSeconds(days * 86400L).toString());
        user.setImages(List.of(id + "-avatar.webp")); return user;
    }

    private Document message(String id, String sender, String text) {
        return new Document("_id", id).append("id", id).append("chatId", "conversation")
                .append("sender", sender).append("senderAvatar", new Document("id", sender).append("name", sender))
                .append("messageSenderUserIds", List.of(sender)).append("text", text)
                .append("sentAtIso", "2026-01-01T00:00:00Z");
    }

    private DeletedAccountPurgeService worker(UsersRepository users, AccountPurgeJobsRepository jobs,
            ChatPrivacyService chat, MediaCleanup media, MongoTemplate mongo) {
        var worker = new DeletedAccountPurgeService(users, chat, jobs, media, mongo, null);
        var scheduling = mock(NotificationJobs.class);
        doAnswer(call -> { JobGroupContext.run(null, call.getArgument(1, Runnable.class)); return null; })
                .when(scheduling).forEachGroup(eq(NotificationJobs.RULE_ACCOUNT_PURGE), any(Runnable.class));
        worker.setNotificationRuleRegistryService(scheduling);
        ReflectionTestUtils.setField(worker, "jobGroupScope", new JobGroupScopeService(
                mock(ProfileDirectory.class), Optional.empty(), List.of()));
        ReflectionTestUtils.setField(worker, "notificationPrivacy", new NotificationPrivacyService(mongo));
        ReflectionTestUtils.setField(worker, "helpPrivacy", new HelpPrivacyService(mongo));
        ReflectionTestUtils.setField(worker, "integrationPrivacy",
                new com.raxim.myscoutee.domain.integrations.service.IntegrationPrivacyService(mongo));
        return worker;
    }
}
