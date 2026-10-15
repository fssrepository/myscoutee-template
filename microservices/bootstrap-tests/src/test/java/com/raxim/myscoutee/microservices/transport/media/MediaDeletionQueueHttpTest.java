package com.raxim.myscoutee.microservices.transport.media;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.media.api.MediaDeletionQueue;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.microservices.bootstrap.ServiceSecurityConfiguration;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport.RemoteReadException;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaDeletionQueueHttpTest {
    static final String TOKEN = "test-only-media-deletion-0000000000000000000000000";
    static final Instant START = Instant.parse("2026-10-14T14:00:00Z");
    static final String REFERENCE = "/api/media/private?key=private/documents/owner/entity/file.pdf";
    static final tools.jackson.databind.ObjectMapper mapper = new com.raxim.myscoutee.common.config.JsonConfig().objectMapper();
    record Call(String action, MongoSessionMode mode, String group, String actor, Instant started, List<String> references) {}
    static final List<Call> calls = new CopyOnWriteArrayList<>();
    static final AtomicInteger sequence = new AtomicInteger();
    static volatile boolean failOwner;
    static ConfigurableApplicationContext server;
    static HttpClient http;
    static URI origin;
    static Path evidence;
    @BeforeAll static void start() throws Exception {
        evidence = Path.of(System.getProperty("wireEvidenceDirectory"), "media-deletion"); Files.createDirectories(evidence);
        try (var files = Files.list(evidence)) { for (var path : files.filter(p -> p.getFileName().toString().startsWith("response-")).toList()) Files.delete(path); }
        server = new SpringApplicationBuilder(Fixture.class).properties(
                "server.address=127.0.0.1", "server.port=0", "management.server.port=0",
                "myscoutee.remote.media-deletion.command-token=" + TOKEN,
                "spring.main.banner-mode=off", "logging.level.root=WARN").run();
        origin = URI.create("http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port"));
        http = HttpClient.newHttpClient();
    }
    @BeforeEach void reset() { calls.clear(); failOwner = false; }
    @AfterEach void clear() { MongoSessionModeContext.clear(); }
    @AfterAll static void stop() { if (http != null) http.shutdownNow(); if (server != null) server.close(); }
    static String body(String path) {
        return mapper.writeValueAsString(path.equals(MediaDeletionQueueProtocol.RUN)
                ? new MediaDeletionQueueProtocol.Command(true, "myscoutee-community", "admin", START)
                : new MediaDeletionQueueProtocol.Enqueue("myscoutee-community", List.of(REFERENCE)));
    }
    @Test void bothCommandsPreserveDatabaseAndWorkspaceAndUseNativeOwner() throws Exception {
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        try (var remote = new RemoteMediaDeletionQueue(origin, TOKEN, true, Duration.ofSeconds(1), Duration.ofSeconds(3), mapper, meters)) {
            for (var mode : MongoSessionMode.values()) {
                MongoSessionModeContext.set(mode);
                JobGroupContext.run("myscoutee-community", () -> {
                    remote.enqueue("myscoutee-community", List.of(REFERENCE));
                    assertEquals(new Call("enqueue", mode, "myscoutee-community", null, null, List.of(REFERENCE)), calls.getLast());
                    remote.run("admin", START);
                    assertEquals(new Call("run", mode, "myscoutee-community", "admin", START, null), calls.getLast());
                });
                for (var path : MediaDeletionQueueProtocol.paths()) assertEquals(200, request(path, body(path), TOKEN, mode.name(), "POST").statusCode());
            }
            assertFalse(JobGroupContext.isActive());
            failOwner = true; int before = calls.size();
            assertThrows(RemoteReadException.class, () -> remote.enqueue("myscoutee-community", List.of(REFERENCE)));
            assertEquals(before + 1, calls.size(), "Lost acknowledgement must not trigger an implicit transport retry");
        } finally { meters.close(); }
    }
    @Test void invalidCredentialsModeAndPayloadNeverReachOwner() throws Exception {
        for (var path : MediaDeletionQueueProtocol.paths()) {
            assertEquals(401, request(path, body(path), null, "DEMO", "POST").statusCode());
            assertEquals(401, request(path, body(path), TOKEN + "wrong", "DEMO", "POST").statusCode());
            assertEquals(400, request(path, body(path), TOKEN, "INVALID", "POST").statusCode());
            assertEquals(400, request(path, "{\"unknown\":true}", TOKEN, "DEMO", "POST").statusCode());
            assertEquals(400, request(path, body(path) + "{}", TOKEN, "DEMO", "POST").statusCode());
            assertEquals(403, request(path, body(path), TOKEN, "DEMO", "GET").statusCode());
        }
        assertEquals(400, request(MediaDeletionQueueProtocol.ENQUEUE,
                mapper.writeValueAsString(new MediaDeletionQueueProtocol.Enqueue(null, Collections.nCopies(101, REFERENCE))), TOKEN, "DEMO", "POST").statusCode());
        assertTrue(calls.isEmpty());
    }
    @Test void ownerFailureReturnsUnknownOutcomeWithoutSensitiveDetails() throws Exception {
        failOwner = true;
        for (var path : MediaDeletionQueueProtocol.paths()) {
            var response = request(path, body(path), TOKEN, "DEMO", "POST");
            assertEquals(503, response.statusCode()); assertFalse(response.body().contains("private-owner-detail"));
        }
        assertEquals(2, calls.size());
    }
    static HttpResponse<String> request(String path, String body, String token, String mode, String method) throws Exception {
        var request = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").header("X-MyScoutee-Session-Mode", mode);
        if (token != null) request.header("Authorization", "Bearer " + token);
        var response = http.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        Files.writeString(evidence.resolve("response-" + sequence.incrementAndGet() + ".json"), mapper.writeValueAsString(
                Map.of("path", path, "method", "post", "status", response.statusCode(), "body", mapper.readTree(response.body()))));
        return response;
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration", "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
            "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration", "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
    @Import({com.raxim.myscoutee.common.config.JsonConfig.class, ServiceSecurityConfiguration.class, MediaDeletionQueueServerConfiguration.class})
    static class Fixture {
        @Bean MediaDeletionQueue owner() { return new MediaDeletionQueue() {
            public void enqueue(String group, List<String> references) {
                calls.add(new Call("enqueue", MongoSessionModeContext.currentMode(), group, null, null, references));
                if (failOwner) throw new IllegalStateException("private-owner-detail");
            }
            public void run(String actor, Instant startedAt) {
                calls.add(new Call("run", MongoSessionModeContext.currentMode(), JobGroupContext.groupId(), actor, startedAt, null));
                if (failOwner) throw new IllegalStateException("private-owner-detail");
            }
        }; }
    }
}
