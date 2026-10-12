package com.raxim.myscoutee.microservices.transport.notifications;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.accounts.api.*;
import com.raxim.myscoutee.domain.notifications.api.NotificationReads;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationsPageResponseDto;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.microservices.bootstrap.ServiceSecurityConfiguration;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Existing page and unread-count queries retain actor/mode/body-bound authority. */
class NotificationReadsHttpTest {
    private static final String TOKEN = "test-only-notification-read-capability-0000000000000000";
    private static final tools.jackson.databind.ObjectMapper mapper = new com.raxim.myscoutee.common.config.JsonConfig().objectMapper();
    private static final AtomicInteger countCalls = new AtomicInteger(), pageCalls = new AtomicInteger(), sequence = new AtomicInteger();
    private static final AtomicBoolean fail = new AtomicBoolean();
    private static final List<String> contexts = Collections.synchronizedList(new ArrayList<>());
    private static ActorDelegation.Issuer issuer;
    private static ConfigurableApplicationContext server;
    private static RemoteNotificationReads remote;
    private static SimpleMeterRegistry meters;
    private static HttpClient http;
    private static URI origin;
    private static Path evidence;

    @BeforeAll static void start() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        issuer = new ActorDelegation.Issuer(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKey, Clock.systemUTC());
        evidence = Path.of(System.getProperty("wireEvidenceDirectory"), "notification-reads"); Files.createDirectories(evidence);
        try (var files = Files.list(evidence)) {
            for (var file : files.filter(p -> p.getFileName().toString().startsWith("response-")).toList()) Files.delete(file);
        }
        server = new SpringApplicationBuilder(Fixture.class).properties("server.address=127.0.0.1", "server.port=0", "management.server.port=0",
                "myscoutee.remote.notification-reads.read-token=" + TOKEN, "myscoutee.delegation.public-key=" + publicKey,
                "spring.main.banner-mode=off", "logging.level.root=WARN").run();
        origin = URI.create("http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port"));
        meters = new SimpleMeterRegistry(); http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        remote = new RemoteNotificationReads(origin, TOKEN, true, Duration.ofSeconds(1), Duration.ofSeconds(3), mapper, meters, issuer);
    }
    @BeforeEach void prepare() { countCalls.set(0); pageCalls.set(0); contexts.clear(); fail.set(false); clear(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); MongoSessionModeContext.clear(); }
    @AfterAll static void stop() {
        if (remote != null) remote.close(); if (http != null) http.shutdownNow();
        if (server != null) server.close(); if (meters != null) meters.close();
    }
    private static void actor() {
        var profile = mock(WorkspaceProfile.class);
        when(profile.getUserId()).thenReturn("selected"); when(profile.getAccountUserId()).thenReturn("account"); when(profile.getWorkspaceGroupId()).thenReturn("workspace");
        var actor = mock(AccountIdentity.class); when(actor.getUserId()).thenReturn("selected"); when(actor.getUser()).thenReturn(profile);
        when(actor.getEmail()).thenReturn("actor@example.invalid");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of(new SimpleGrantedAuthority("U"))));
    }
    private static String unread(String userId) {
        return mapper.writeValueAsString(new NotificationReadsProtocol.UnreadQuery(new NotificationReadsProtocol.Scope(false, null), userId));
    }
    private static HttpResponse<String> signed(String path, String body, String mode) throws Exception {
        actor(); MongoSessionModeContext.set(MongoSessionMode.valueOf(mode));
        String proof = issuer.issueForCurrent(NotificationReadsProtocol.AUDIENCE, "POST", path, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request(path, body, TOKEN, proof, mode, "application/json", "POST");
    }
    @Test void unreadUsesOneCountAndNoPageQueryInBothModesAndScopes() {
        for (var mode : MongoSessionMode.values()) {
            MongoSessionModeContext.set(mode); actor();
            assertEquals(7, remote.currentUnreadCount("selected"));
            assertEquals(7, JobGroupContext.call(true, "workspace", () -> remote.currentUnreadCount("selected")));
        }
        assertEquals(4, countCalls.get()); assertEquals(0, pageCalls.get());
        assertEquals(Set.of("REAL:false:null", "REAL:true:workspace", "DEMO:false:null", "DEMO:true:workspace"), new HashSet<>(contexts));
    }
    @Test void existingPageOperationRemainsAvailable() throws Exception {
        MongoSessionModeContext.set(MongoSessionMode.DEMO); actor();
        assertEquals(7, remote.queryPage("selected", "all", 1, null, null).unreadCount());
        assertEquals(1, pageCalls.get()); assertEquals(0, countCalls.get());
        String body = mapper.writeValueAsString(new NotificationReadsProtocol.Query(new NotificationReadsProtocol.Scope(false, null), "selected", "all", 1, null, null));
        assertEquals(200, signed(NotificationReadsProtocol.PAGE, body, "DEMO").statusCode());
    }
    @Test void serviceCredentialCannotReadAnotherActorOrReplayDifferentModePathOrBody() throws Exception {
        String path = NotificationReadsProtocol.UNREAD, body = unread("selected");
        assertEquals(403, request(path, body, TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(403, signed(path, unread("other"), "REAL").statusCode());
        actor(); MongoSessionModeContext.set(MongoSessionMode.REAL);
        String proof = issuer.issueForCurrent(NotificationReadsProtocol.AUDIENCE, "POST", path, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(403, request(path, body, TOKEN, proof, "DEMO", "application/json", "POST").statusCode());
        assertEquals(403, request(path, unread("tampered"), TOKEN, proof, "REAL", "application/json", "POST").statusCode());
        assertEquals(403, request(NotificationReadsProtocol.PAGE, body, TOKEN, proof, "REAL", "application/json", "POST").statusCode());
        assertEquals(0, countCalls.get()); assertEquals(0, pageCalls.get());
    }
    @Test void invalidAndOversizedRequestsNeverReachOwner() throws Exception {
        String path = NotificationReadsProtocol.UNREAD, body = unread("selected");
        assertEquals(401, request(path, body, null, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(400, signed(path, "{\"unknown\":true}", "REAL").statusCode());
        assertEquals(400, signed(path, unread("x".repeat(513)), "REAL").statusCode());
        assertEquals(400, signed(path, body + "{}", "REAL").statusCode());
        assertEquals(413, signed(path, " ".repeat(NotificationReadsProtocol.MAX_REQUEST_BYTES + 1), "REAL").statusCode());
        assertEquals(415, request(path, body, TOKEN, "proof", "REAL", "text/plain", "POST").statusCode());
        assertEquals(403, request(path, body, TOKEN, null, "REAL", "application/json", "GET").statusCode());
        assertEquals(0, countCalls.get()); assertEquals(0, pageCalls.get());
    }
    @Test void ownerFailureIsSanitizedAndNotRetried() throws Exception {
        fail.set(true);
        var response = signed(NotificationReadsProtocol.UNREAD, unread("selected"), "REAL");
        assertEquals(503, response.statusCode()); assertFalse(response.body().contains("private"));
        assertEquals(1, countCalls.get());
    }
    @Test void successfulUnreadResponseUsesTheDocumentedWireShape() throws Exception {
        var response = signed(NotificationReadsProtocol.UNREAD, unread("selected"), "REAL");
        assertEquals(200, response.statusCode()); assertEquals("7", response.body()); assertEquals(1, countCalls.get());
    }
    private static HttpResponse<String> request(String path, String body, String token, String proof, String mode, String contentType, String method) throws Exception {
        var builder = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(5))
                .header("X-MyScoutee-Session-Mode", mode).header("Content-Type", contentType);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (proof != null) builder.header(ActorDelegation.HEADER, proof);
        var response = http.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        Files.writeString(evidence.resolve("response-" + sequence.incrementAndGet() + ".json"), mapper.writeValueAsString(
                Map.of("path", path, "method", "post", "status", response.statusCode(), "body", mapper.readTree(response.body()))));
        return response;
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration", "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
            "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration", "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
    @Import({com.raxim.myscoutee.common.config.JsonConfig.class, NotificationReadsServerConfiguration.class, ServiceSecurityConfiguration.class})
    static class Fixture {
        @Bean NotificationReads reads() {
            return new NotificationReads() {
                public int currentUnreadCount(String userId) {
                    countCalls.incrementAndGet(); assertEquals("selected", userId);
                    assertEquals("selected", ((AuthenticatedActor) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId());
                    contexts.add(MongoSessionModeContext.currentMode() + ":" + JobGroupContext.isActive() + ":" + JobGroupContext.groupId());
                    if (fail.get()) throw new IllegalStateException("private owner details");
                    return 7;
                }
                public UserNotificationsPageResponseDto queryPage(String userId, String bucket, Integer limit, String cursor, String workspace) {
                    pageCalls.incrementAndGet(); assertEquals("selected", userId);
                    return new UserNotificationsPageResponseDto(List.of(), 0L, null, 7, false);
                }
            };
        }
    }
}
