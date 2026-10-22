package com.raxim.myscoutee.microservices.transport.chat;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.chat.document.Chat;
import com.raxim.myscoutee.domain.chat.dto.ChatDirectoryEntryDto;
import com.raxim.myscoutee.domain.chat.repository.ChatsRepository;
import com.raxim.myscoutee.domain.chat.service.ChatDirectoryService;
import com.raxim.myscoutee.microservices.bootstrap.ServiceSecurityConfiguration;
import com.raxim.myscoutee.microservices.transport.common.InternalReadCredentialFilter;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport.RemoteReadException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatDirectoryHttpTest {
    static final String TOKEN = "test-only-chat-directory-token-000000000000000000";
    static ConfigurableApplicationContext server;
    static RemoteChatDirectory remote;
    static HttpClient http;
    static URI origin;
    static SimpleMeterRegistry meters;
    static Path evidence;
    static final AtomicInteger sequence = new AtomicInteger();

    @BeforeAll static void start() throws Exception {
        evidence = Path.of(System.getProperty("wireEvidenceDirectory"), "chat-directory");
        Files.createDirectories(evidence);
        try (var files = Files.list(evidence)) {
            for (var file : files.filter(p -> p.getFileName().toString().matches("response-[0-9]+\\.json")).toList()) Files.delete(file);
        }
        server = new SpringApplicationBuilder(Fixture.class).properties(
                "server.address=127.0.0.1", "server.port=0", "management.server.port=0",
                "myscoutee.remote.chat-directory.read-token=" + TOKEN,
                "spring.main.allow-bean-definition-overriding=false",
                "spring.main.banner-mode=off", "logging.level.root=WARN").run();
        origin = URI.create("http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port"));
        http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        meters = new SimpleMeterRegistry();
        remote = new RemoteChatDirectory(origin, TOKEN, true, Duration.ofSeconds(1), Duration.ofSeconds(3), new JsonMapper(), meters);
    }
    @AfterEach void clear() { MongoSessionModeContext.clear(); }
    @AfterAll static void stop() {
        if (remote != null) remote.close();
        if (http != null) http.shutdownNow();
        if (server != null) server.close();
        if (meters != null) meters.close();
    }
    @Test void viewsPreserveCanonicalFieldsOwnerLookupAndModesWithoutExposingChatDocuments() throws Exception {
        var local = server.getBean(ChatDirectoryService.class);
        for (var mode : List.of(MongoSessionMode.REAL, MongoSessionMode.DEMO, MongoSessionMode.REAL)) {
            MongoSessionModeContext.set(mode);
            for (var id : List.of("known", "unset", "missing")) {
                var expected = local.findByOwnerUserIdAndChatId("owner&é/?", id).map(ChatDirectoryEntryDto::from);
                assertEquals(expected, remote.findByOwnerUserIdAndChatId("owner&é/?", id));
                assertEquals(local.findByChatId(id).stream().map(ChatDirectoryEntryDto::from).toList(), remote.findByChatId(id));
                for (var path : List.of(ChatDirectoryProtocol.ENTRY, ChatDirectoryProtocol.ENTRIES)) {
                    var response = request(path, "?ownerId=owner&chatId=" + id, TOKEN, mode.name(), "GET");
                    assertEquals(path.equals(ChatDirectoryProtocol.ENTRY) && id.equals("missing") ? 404 : 200, response.statusCode());
                    assertFalse(response.body().contains("secret-message"));
                    assertFalse(response.body().contains("mongoId"));
                    assertFalse(response.body().contains("revision"));
                }
                if (!id.equals("missing")) assertEquals(mode.name(), remote.findByOwnerUserIdAndChatId("owner", id).orElseThrow().channelType());
            }
        }
        var unset = remote.findByOwnerUserIdAndChatId(null, "unset").orElseThrow();
        assertEquals(List.of(), unset.memberIds()); assertNull(unset.supportCase()); assertNull(unset.unread());
        var entry = remote.findByOwnerUserIdAndChatId("owner", "known").orElseThrow();
        assertEquals(Arrays.asList("member", null), entry.memberIds());
        assertEquals("pending", entry.getSupportCase().getStatus());
    }
    @Test void unreadAndCountsKeepNullEmptyAndCanonicalAggregateSemantics() throws Exception {
        var local = server.getBean(ChatDirectoryService.class);
        for (var mode : List.of(MongoSessionMode.REAL, MongoSessionMode.DEMO)) {
            MongoSessionModeContext.set(mode);
            for (var id : List.of("known", "unset", "missing")) {
                assertEquals(local.unreadVersion(id), remote.unreadVersion(id));
                assertEquals(200, request(ChatDirectoryProtocol.UNREAD, "?ownerId=" + id, TOKEN, mode.name(), "GET").statusCode());
            }
            assertEquals(local.supportCaseCounts("base&é/?"), remote.supportCaseCounts("base&é/?"));
            assertEquals(Map.of(mode.name(), 3, "warned", 0), remote.supportCaseCounts("base"));
            assertEquals(200, request(ChatDirectoryProtocol.COUNTS, "?baseGroupId=base", TOKEN, mode.name(), "GET").statusCode());
        }
        assertEquals("", remote.unreadVersion("missing"));
        assertNull(remote.unreadVersion("unset"));
    }
    @Test void everyRouteRejectsForeignCredentialsWrongModeMethodAndOversizedParameters() throws Exception {
        for (var path : ChatDirectoryProtocol.paths()) {
            for (var token : new String[]{null, "test-only-chat-media-token-0000000000000000000000"})
                assertEquals(401, request(path, "", token, "REAL", "GET").statusCode());
            for (var mode : new String[]{null, "demo", "ANY"})
                assertEquals(400, request(path, "", TOKEN, mode, "GET").statusCode());
            assertEquals(403, request(path, "", TOKEN, "REAL", "POST").statusCode());
            String key = path.equals(ChatDirectoryProtocol.UNREAD) ? "ownerId" : path.equals(ChatDirectoryProtocol.COUNTS) ? "baseGroupId" : "chatId";
            assertEquals(400, request(path, "?" + key + "=" + "x".repeat(513), TOKEN, "REAL", "GET").statusCode());
        }
        assertThrows(IllegalArgumentException.class, () -> remote.findByChatId("x".repeat(513)));
    }
    @Test void ownerFailureCannotBecomeAnEmptyResultAndNullableParametersAreForwarded() throws Exception {
        for (var path : ChatDirectoryProtocol.paths()) {
            var response = request(path, "?ownerId=fail&chatId=fail&baseGroupId=fail", TOKEN, "REAL", "GET");
            assertEquals(503, response.statusCode());
            assertEquals("{\"code\":\"CHAT_DIRECTORY_UNAVAILABLE\"}", response.body());
        }
        assertEquals(503, assertThrows(RemoteReadException.class, () -> remote.findByChatId("fail")).status());
        assertEquals(503, assertThrows(RemoteReadException.class, () -> remote.findByOwnerUserIdAndChatId("id", "fail")).status());
        assertEquals(503, assertThrows(RemoteReadException.class, () -> remote.unreadVersion("fail")).status());
        assertEquals(503, assertThrows(RemoteReadException.class, () -> remote.supportCaseCounts("fail")).status());
        var repository = server.getBean(ChatsRepository.class);
        for (String owner : new String[]{null, ""}) {
            remote.findByOwnerUserIdAndChatId(owner, null);
            verify(repository).findByOwnerUserIdAndChatId(owner, null);
        }
    }
    static HttpResponse<String> request(String path, String suffix, String token, String mode, String method) throws Exception {
        var builder = HttpRequest.newBuilder(origin.resolve(path + suffix)).timeout(Duration.ofSeconds(3));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (mode != null) builder.header(InternalReadCredentialFilter.MODE_HEADER, mode);
        var response = http.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        Files.writeString(evidence.resolve("response-" + sequence.incrementAndGet() + ".json"), new JsonMapper().writeValueAsString(
                Map.of("path", path, "status", response.statusCode(), "body", new JsonMapper().readTree(response.body()))));
        return response;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
            "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
            "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
    @Import({ChatDirectoryServerConfiguration.class, ServiceSecurityConfiguration.class})
    static class Fixture {
        @Bean ChatsRepository chats() {
            var repository = mock(ChatsRepository.class);
            when(repository.findByOwnerUserIdAndChatId(nullable(String.class), nullable(String.class))).thenAnswer(call -> {
                String id = call.getArgument(1);
                if ("fail".equals(id)) throw new IllegalStateException("secret database details");
                return "missing".equals(id) ? Optional.empty() : Optional.of(chat(call.getArgument(0), id));
            });
            when(repository.findByChatId(nullable(String.class))).thenAnswer(call -> {
                String id = call.getArgument(0);
                if ("fail".equals(id)) throw new IllegalStateException("secret database details");
                return "missing".equals(id) ? List.of() : List.of(chat("first-owner", id), chat("second-owner", id));
            });
            return repository;
        }
        static Chat chat(String owner, String id) {
            var chat = new Chat();
            chat.setChatId(id); chat.setOwnerUserId(owner); chat.setOwnerId("case-id");
            chat.setChannelType(MongoSessionModeContext.currentMode().name());
            chat.setTitle("Visible title"); chat.setSupportBaseGroupId("base");
            chat.setMongoId("persistence-only"); chat.setLastMessage("secret-message");
            chat.setUnread("unset".equals(id) ? null : 4);
            chat.setMemberIds("unset".equals(id) ? null : Arrays.asList("member", null));
            if (!"unset".equals(id)) {
                var support = new Chat.SupportCaseRecord(); support.setStatus("pending"); chat.setSupportCase(support);
            }
            return chat;
        }
        @Bean MongoTemplate mongo() {
            var mongo = mock(MongoTemplate.class);
            when(mongo.findOne(any(Query.class), eq(Document.class), eq("chats"))).thenAnswer(call -> {
                String owner = ((Query) call.getArgument(0)).getQueryObject().getString("ownerUserId");
                if ("fail".equals(owner)) throw new IllegalStateException("secret database details");
                return "missing".equals(owner) ? null : new Document("dateIso", "unset".equals(owner) ? null : MongoSessionModeContext.currentMode().name());
            });
            when(mongo.aggregate(any(Aggregation.class), eq(Chat.class), eq(Document.class))).thenAnswer(call -> {
                var pipeline = ((Aggregation) call.getArgument(0)).toPipeline(Aggregation.DEFAULT_CONTEXT);
                if ("fail".equals(((Document) pipeline.get(0).get("$match")).get("supportBaseGroupId"))) throw new IllegalStateException("secret database details");
                return new AggregationResults<>(List.of(new Document("_id", MongoSessionModeContext.currentMode().name()).append("count", 3L),
                        new Document("_id", "warned").append("count", -1)), new Document());
            });
            return mongo;
        }
        @Bean ChatDirectoryService owner(ChatsRepository repository, MongoTemplate mongo) {
            return new ChatDirectoryService(repository, mongo);
        }
    }
}
