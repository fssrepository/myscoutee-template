package com.raxim.myscoutee.microservices.transport.access;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.raxim.myscoutee.common.config.mongo.*;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.accounts.api.*;
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

/** Transport authority tests; native policy rules remain covered by AccessControlServiceTest. */
class AccessPermissionsHttpTest {
    private static final String TOKEN = "test-only-access-permission-token-000000000000000000000";
    private static final tools.jackson.databind.ObjectMapper mapper = new com.raxim.myscoutee.common.config.JsonConfig().objectMapper();
    private static final AtomicInteger calls = new AtomicInteger(), sequence = new AtomicInteger();
    private static ActorDelegation.Issuer issuer;
    private static ConfigurableApplicationContext server;
    private static RemoteAccessPermissions remote;
    private static SimpleMeterRegistry meters;
    private static HttpClient http;
    private static URI origin;
    private static Path evidence;

    @BeforeAll static void start() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        issuer = new ActorDelegation.Issuer(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKey, Clock.systemUTC());
        evidence = Path.of(System.getProperty("wireEvidenceDirectory"), "access-permissions"); Files.createDirectories(evidence);
        try (var files = Files.list(evidence)) {
            for (var file : files.filter(p -> p.getFileName().toString().startsWith("response-")).toList()) Files.delete(file);
        }
        server = new SpringApplicationBuilder(Fixture.class).properties("server.address=127.0.0.1", "server.port=0", "management.server.port=0",
                "myscoutee.remote.access-permissions.read-token=" + TOKEN, "myscoutee.delegation.public-key=" + publicKey,
                "spring.main.banner-mode=off", "logging.level.root=WARN").run();
        origin = URI.create("http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port"));
        meters = new SimpleMeterRegistry(); http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        remote = new RemoteAccessPermissions(origin, TOKEN, true, Duration.ofSeconds(1), Duration.ofSeconds(3), mapper, meters, issuer);
    }
    @BeforeEach void prepare() { calls.set(0); SecurityContextHolder.clearContext(); MongoSessionModeContext.clear(); }
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
    @Test void serviceCapabilityIsNeverAnActorAndNativeAbsenceDecisionIsPreserved() throws Exception {
        for (var mode : MongoSessionMode.values()) {
            MongoSessionModeContext.set(mode);
            assertEquals(mode == MongoSessionMode.DEMO, remote.canCurrentActorEditChatMessage("subject", "asset"));
            assertEquals(mode != MongoSessionMode.DEMO, remote.requiresAuthenticatedActor());
            assertEquals(200, request(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE,
                    "{\"userId\":\"subject\",\"chatId\":\"asset\"}", TOKEN, null, mode.name(), "application/json", "POST").statusCode());
        }
    }
    @Test void verifiedSelectionAndModeReachOwnerWhileSubjectArgumentsRemainDistinct() throws Exception {
        for (var mode : MongoSessionMode.values()) {
            MongoSessionModeContext.set(mode); actor();
            assertTrue(remote.canCurrentActorEditChatMessage("subject", "asset"));
            assertFalse(remote.canCurrentActorEditChatMessage("wrong", "asset"));
            assertEquals("selected", remote.resolveChatActorUserId("chat", "selected"));
            String path = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE;
            String body = "{\"userId\":\"subject\",\"chatId\":\"asset\"}";
            String proof = issuer.issueForCurrent(AccessPermissionsProtocol.AUDIENCE, "POST", path, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(200, request(path, body, TOKEN, proof, mode.name(), "application/json", "POST").statusCode());
            assertEquals("selected", ((AuthenticatedActor) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId());
        }
    }
    @Test void badProofNeverFallsBackEvenInDemoAndIsBoundToBodyOperationAndMode() throws Exception {
        actor(); MongoSessionModeContext.set(MongoSessionMode.REAL);
        String path = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE, body = "{\"userId\":\"subject\",\"chatId\":\"asset\"}";
        String proof = issuer.issueForCurrent(AccessPermissionsProtocol.AUDIENCE, "POST", path, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(403, request(path, body, TOKEN, proof, "DEMO", "application/json", "POST").statusCode());
        assertEquals(403, request(path, body.replace("subject", "forged"), TOKEN, proof, "REAL", "application/json", "POST").statusCode());
        assertEquals(403, request(AccessPermissionsProtocol.CAN_CURRENT_ACTOR_DELETE_CHAT_MESSAGE, body, TOKEN, proof, "REAL", "application/json", "POST").statusCode());
        assertEquals(403, request(path, body, TOKEN, "broken", "DEMO", "application/json", "POST").statusCode());
        assertEquals(0, calls.get());
    }
    @Test void boundsCredentialsMethodsAndOwnerFailureRemainExplicit() throws Exception {
        String path = AccessPermissionsProtocol.CAN_CURRENT_ACTOR_EDIT_CHAT_MESSAGE, body = "{\"userId\":\"subject\",\"chatId\":\"asset\"}";
        assertEquals(401, request(path, body, null, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(400, request(path, body, TOKEN, null, "bad", "application/json", "POST").statusCode());
        assertEquals(400, request(path, "{\"unknown\":1}", TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(400, request(path, body + "{}", TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(413, request(path, " ".repeat(AccessPermissionsProtocol.MAX_REQUEST_BYTES + 1), TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(415, request(path, body, TOKEN, null, "REAL", "text/plain", "POST").statusCode());
        assertEquals(403, request(path, body, TOKEN, null, "REAL", "application/json", "GET").statusCode());
        assertEquals(0, calls.get());
        var failed = request(path, body.replace("asset\"", "fail\""), TOKEN, null, "REAL", "application/json", "POST");
        assertEquals(503, failed.statusCode()); assertFalse(failed.body().contains("private"));
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
    @Import({com.raxim.myscoutee.common.config.JsonConfig.class, AccessPermissionsServerConfiguration.class, ServiceSecurityConfiguration.class})
    static class Fixture {
        @Bean AccessPermissions permissions() {
            var owner = mock(AccessPermissions.class);
            when(owner.canCurrentActorEditChatMessage(any(), any())).thenAnswer(call -> {
                calls.incrementAndGet();
                if ("fail".equals(call.getArgument(1))) throw new IllegalStateException("private owner error");
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null) return MongoSessionModeContext.currentMode() == MongoSessionMode.DEMO;
                var actor = assertInstanceOf(AuthenticatedActor.class, auth.getPrincipal());
                assertEquals("selected", actor.getUserId()); assertEquals("workspace", actor.getProfileIdentity().workspaceGroupId());
                assertEquals(List.of(new SimpleGrantedAuthority("U")), auth.getAuthorities());
                return "subject".equals(call.getArgument(0)) && "asset".equals(call.getArgument(1));
            });
            when(owner.requiresAuthenticatedActor()).thenAnswer(call -> MongoSessionModeContext.currentMode() != MongoSessionMode.DEMO);
            when(owner.resolveChatActorUserId(any(), any())).thenAnswer(call -> call.getArgument(1));
            return owner;
        }
    }
}
