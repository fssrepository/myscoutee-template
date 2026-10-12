package com.raxim.myscoutee.microservices.transport.accounts;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.raxim.myscoutee.common.config.mongo.*;
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

/** Media cookie checks and signed session rotation preserve native session isolation. */
class AccountMediaSessionsHttpTest {
    private static final String TOKEN = "test-only-access-permission-token-000000000000000000000";
    private static final tools.jackson.databind.ObjectMapper mapper = new com.raxim.myscoutee.common.config.JsonConfig().objectMapper();
    private static final AtomicInteger calls = new AtomicInteger(), sequence = new AtomicInteger();
    private static ActorDelegation.Issuer issuer;
    private static ConfigurableApplicationContext server;
    private static RemoteAccountMediaSessions remote;
    private static SimpleMeterRegistry meters;
    private static HttpClient http;
    private static URI origin;
    private static Path evidence;

    @BeforeAll static void start() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        issuer = new ActorDelegation.Issuer(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKey, Clock.systemUTC());
        evidence = Path.of(System.getProperty("wireEvidenceDirectory"), "account-media-sessions"); Files.createDirectories(evidence);
        try (var files = Files.list(evidence)) {
            for (var file : files.filter(p -> p.getFileName().toString().startsWith("response-")).toList()) Files.delete(file);
        }
        server = new SpringApplicationBuilder(Fixture.class).properties("server.address=127.0.0.1", "server.port=0", "management.server.port=0",
                "myscoutee.remote.account-media-sessions.command-token=" + TOKEN, "myscoutee.delegation.public-key=" + publicKey,
                "spring.main.banner-mode=off", "logging.level.root=WARN").run();
        origin = URI.create("http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port"));
        meters = new SimpleMeterRegistry(); http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        remote = new RemoteAccountMediaSessions(origin, TOKEN, true, Duration.ofSeconds(1), Duration.ofSeconds(3), mapper, meters, issuer);
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
    private static final String HASH = "a".repeat(64);
    private static String session(String userId, String sessionId) {
        return mapper.writeValueAsString(new AccountMediaSessionsProtocol.Session(userId, sessionId));
    }
    private static HttpResponse<String> signed(String path, String body, String mode) throws Exception {
        actor(); MongoSessionModeContext.set(MongoSessionMode.valueOf(mode));
        String proof = issuer.issueForCurrent(AccountMediaSessionsProtocol.AUDIENCE, "POST", path, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request(path, body, TOKEN, proof, mode, "application/json", "POST");
    }
    @Test void checksAndSignedSessionOperationsKeepNativeResultsInEachRealm() throws Exception {
        for (var mode : MongoSessionMode.values()) {
            MongoSessionModeContext.set(mode); SecurityContextHolder.clearContext();
            assertTrue(remote.acceptsMediaToken("selected", HASH));
            assertFalse(remote.acceptsMediaToken("selected", "b".repeat(64)));
            actor();
            assertEquals(HASH, remote.mediaToken("selected", "session").orElseThrow().hash());
            assertNull(remote.mediaToken("selected", "fresh").orElseThrow().hash());
            assertTrue(remote.mediaToken("selected", "missing").isEmpty());
            assertTrue(remote.replaceMediaToken("selected", "session", HASH));
            assertFalse(remote.replaceMediaToken("selected", "missing", HASH));
            assertEquals(200, request(AccountMediaSessionsProtocol.ACCEPTS,
                    mapper.writeValueAsString(new AccountMediaSessionsProtocol.Check("selected", HASH)), TOKEN, null, mode.name(), "application/json", "POST").statusCode());
            assertEquals(200, signed(AccountMediaSessionsProtocol.CURRENT, session("selected", "fresh"), mode.name()).statusCode());
            assertEquals(200, signed(AccountMediaSessionsProtocol.REPLACE,
                    mapper.writeValueAsString(new AccountMediaSessionsProtocol.Replacement("selected", "session", HASH)), mode.name()).statusCode());
        }
        assertEquals(20, calls.get());
    }
    @Test void serviceCapabilityCannotReadOrRotateAnotherSelectedProfilesSession() throws Exception {
        String path = AccountMediaSessionsProtocol.CURRENT, body = session("selected", "session");
        assertEquals(403, request(path, body, TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(403, signed(path, session("other", "session"), "REAL").statusCode());
        String proof = issuer.issueForCurrent(AccountMediaSessionsProtocol.AUDIENCE, "POST", path, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(403, request(path, body, TOKEN, proof, "DEMO", "application/json", "POST").statusCode());
        assertEquals(403, request(path, session("selected", "changed"), TOKEN, proof, "REAL", "application/json", "POST").statusCode());
        assertEquals(403, request(AccountMediaSessionsProtocol.REPLACE, body, TOKEN, proof, "REAL", "application/json", "POST").statusCode());
        assertEquals(0, calls.get());
    }
    @Test void malformedAndUnauthenticatedRequestsNeverReachSessionStorage() throws Exception {
        String path = AccountMediaSessionsProtocol.ACCEPTS;
        String body = mapper.writeValueAsString(new AccountMediaSessionsProtocol.Check("selected", HASH));
        assertEquals(401, request(path, body, null, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(400, request(path, "{\"unknown\":true}", TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(400, request(path, body + "{}", TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(400, request(path, body.replace(HASH, "bad"), TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(413, request(path, " ".repeat(AccountMediaSessionsProtocol.MAX_REQUEST_BYTES + 1), TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(415, request(path, body, TOKEN, null, "REAL", "text/plain", "POST").statusCode());
        assertEquals(403, request(path, body, TOKEN, null, "REAL", "application/json", "GET").statusCode());
        assertEquals(0, calls.get());
    }
    @Test void ownerFailureIsNotReportedAsAnAbsentSessionOrRetried() throws Exception {
        var failed = signed(AccountMediaSessionsProtocol.CURRENT, session("selected", "fail"), "REAL");
        assertEquals(503, failed.statusCode()); assertFalse(failed.body().contains("private"));
        assertEquals(1, calls.get());
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
    @Import({com.raxim.myscoutee.common.config.JsonConfig.class, AccountMediaSessionsServerConfiguration.class, ServiceSecurityConfiguration.class})
    static class Fixture {
        @Bean SelectedProfile profiles() {
            return requested -> {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                var actor = assertInstanceOf(AuthenticatedActor.class, auth.getPrincipal());
                assertEquals("selected", actor.getUserId());
                assertEquals(List.of(new SimpleGrantedAuthority("U")), auth.getAuthorities());
                assertNotNull(MongoSessionModeContext.currentMode());
                return new com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto(requested, "account", "workspace");
            };
        }
        @Bean AccountMediaSessions sessions() {
            return new AccountMediaSessions() {
                public boolean acceptsMediaToken(String userId, String hash) {
                    calls.incrementAndGet(); assertEquals("selected", userId);
                    return HASH.equals(hash);
                }
                public Optional<MediaToken> mediaToken(String userId, String sessionId) {
                    calls.incrementAndGet(); assertEquals("selected", userId);
                    if ("fail".equals(sessionId)) throw new IllegalStateException("private owner details");
                    return "missing".equals(sessionId) ? Optional.empty() : Optional.of(new MediaToken("fresh".equals(sessionId) ? null : HASH));
                }
                public boolean replaceMediaToken(String userId, String sessionId, String hash) {
                    calls.incrementAndGet(); assertEquals("selected", userId); assertEquals(HASH, hash);
                    return !"missing".equals(sessionId);
                }
            };
        }
    }
}
