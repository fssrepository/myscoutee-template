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

/** Signed device commands use the native owner only after selected-profile validation. */
class AccountDevicesHttpTest {
    private static final String TOKEN = "test-only-access-permission-token-000000000000000000000";
    private static final tools.jackson.databind.ObjectMapper mapper = new com.raxim.myscoutee.common.config.JsonConfig().objectMapper();
    private static final AtomicInteger calls = new AtomicInteger(), sequence = new AtomicInteger();
    private static ActorDelegation.Issuer issuer;
    private static ConfigurableApplicationContext server;
    private static RemoteAccountDevices remote;
    private static SimpleMeterRegistry meters;
    private static HttpClient http;
    private static URI origin;
    private static Path evidence;

    @BeforeAll static void start() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        issuer = new ActorDelegation.Issuer(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKey, Clock.systemUTC());
        evidence = Path.of(System.getProperty("wireEvidenceDirectory"), "account-devices"); Files.createDirectories(evidence);
        try (var files = Files.list(evidence)) {
            for (var file : files.filter(p -> p.getFileName().toString().startsWith("response-")).toList()) Files.delete(file);
        }
        server = new SpringApplicationBuilder(Fixture.class).properties("server.address=127.0.0.1", "server.port=0", "management.server.port=0",
                "myscoutee.remote.account-devices.command-token=" + TOKEN, "myscoutee.delegation.public-key=" + publicKey,
                "spring.main.banner-mode=off", "logging.level.root=WARN").run();
        origin = URI.create("http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port"));
        meters = new SimpleMeterRegistry(); http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        remote = new RemoteAccountDevices(origin, TOKEN, true, Duration.ofSeconds(1), Duration.ofSeconds(3), mapper, meters, issuer);
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
    private static final String REGISTER = AccountDevicesProtocol.REGISTER;
    private static String registration(String userId, String device) {
        return mapper.writeValueAsString(new AccountDevicesProtocol.Register(userId, device, "push-token", "web", null));
    }
    private static HttpResponse<String> signed(String path, String body, String mode) throws Exception {
        actor(); MongoSessionModeContext.set(MongoSessionMode.valueOf(mode));
        String proof = issuer.issueForCurrent(AccountDevicesProtocol.AUDIENCE, "POST", path, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request(path, body, TOKEN, proof, mode, "application/json", "POST");
    }
    @Test void selectedActorAndSessionModeReachNativeDeviceCommands() throws Exception {
        for (var mode : MongoSessionMode.values()) {
            MongoSessionModeContext.set(mode); actor();
            remote.register("selected", "device", "push-token", "web", null);
            remote.remove("selected", "device", "push-token");
            assertEquals(200, signed(REGISTER, registration("selected", "device"), mode.name()).statusCode());
            assertEquals(200, signed(AccountDevicesProtocol.REMOVE,
                    mapper.writeValueAsString(new AccountDevicesProtocol.Remove("selected", "device", "push-token")), mode.name()).statusCode());
        }
        assertEquals(8, calls.get());
    }
    @Test void capabilityAloneOrForgedSelectionCannotWrite() throws Exception {
        String body = registration("selected", "device");
        assertEquals(403, request(REGISTER, body, TOKEN, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(403, signed(REGISTER, registration("other", "device"), "REAL").statusCode());
        String proof = issuer.issueForCurrent(AccountDevicesProtocol.AUDIENCE, "POST", REGISTER, body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(403, request(REGISTER, body, TOKEN, proof, "DEMO", "application/json", "POST").statusCode());
        assertEquals(403, request(REGISTER, registration("selected", "tampered"), TOKEN, proof, "REAL", "application/json", "POST").statusCode());
        assertEquals(403, request(AccountDevicesProtocol.REMOVE, body, TOKEN, proof, "REAL", "application/json", "POST").statusCode());
        assertEquals(0, calls.get());
    }
    @Test void malformedOversizedAndUnauthenticatedRequestsDoNotWrite() throws Exception {
        String body = registration("selected", "device");
        assertEquals(401, request(REGISTER, body, null, null, "REAL", "application/json", "POST").statusCode());
        assertEquals(400, signed(REGISTER, "{\"unknown\":true}", "REAL").statusCode());
        assertEquals(400, signed(REGISTER, registration("selected", "x".repeat(513)), "REAL").statusCode());
        assertEquals(400, signed(REGISTER, body + "{}", "REAL").statusCode());
        assertEquals(413, signed(REGISTER, " ".repeat(AccountDevicesProtocol.MAX_REQUEST_BYTES + 1), "REAL").statusCode());
        assertEquals(415, request(REGISTER, body, TOKEN, "proof", "REAL", "text/plain", "POST").statusCode());
        assertEquals(403, request(REGISTER, body, TOKEN, null, "REAL", "application/json", "GET").statusCode());
        assertEquals(0, calls.get());
    }
    @Test void ownerFailureIsSanitizedAndNotRetried() throws Exception {
        var failed = signed(REGISTER, registration("selected", "fail"), "REAL");
        assertEquals(503, failed.statusCode());
        assertFalse(failed.body().contains("private"));
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
    @Import({com.raxim.myscoutee.common.config.JsonConfig.class, AccountDevicesServerConfiguration.class, ServiceSecurityConfiguration.class})
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
        @Bean AccountDevices devices() {
            return new AccountDevices() {
                public void register(String userId, String deviceId, String token, String platform, Boolean enabled) {
                    calls.incrementAndGet();
                    assertEquals("selected", userId);
                    assertEquals("push-token", token); assertEquals("web", platform); assertNull(enabled);
                    if ("fail".equals(deviceId)) throw new IllegalStateException("private owner details");
                }
                public void remove(String userId, String deviceId, String token) {
                    calls.incrementAndGet(); assertEquals("selected", userId); assertEquals("push-token", token);
                }
            };
        }
    }
}
