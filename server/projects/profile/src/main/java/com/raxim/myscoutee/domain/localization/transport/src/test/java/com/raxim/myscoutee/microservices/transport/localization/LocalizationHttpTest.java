package com.raxim.myscoutee.microservices.transport.localization;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.localization.document.I18nBundle;
import com.raxim.myscoutee.domain.localization.document.I18nMessage;
import com.raxim.myscoutee.domain.localization.repository.I18nBundleRepository;
import com.raxim.myscoutee.domain.localization.repository.I18nMessageRepository;
import com.raxim.myscoutee.domain.localization.service.I18nService;
import com.raxim.myscoutee.microservices.bootstrap.ServiceSecurityConfiguration;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real loopback HTTP + canonical I18nService; repository fixtures are explicitly mocked. */
class LocalizationHttpTest {
    static final String TOKEN = "test-only-localization-token-0000000000000000000000";
    static ConfigurableApplicationContext server;
    static HttpClient http;
    static RemoteTranslationBundles remote;
    static SimpleMeterRegistry meters;
    static URI origin;
    static int managementPort;
    static Path evidence;
    static AtomicInteger evidenceNumber = new AtomicInteger();

    @BeforeAll static void start() throws Exception {
        evidence = Path.of(System.getProperty("wireEvidenceDirectory"));
        Files.createDirectories(evidence);
        try (var files = Files.list(evidence)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("response-[0-9]+\\.json")).toList()) Files.delete(file);
        }
        try (var socket = new ServerSocket(0)) { managementPort = socket.getLocalPort(); }
        server = new SpringApplicationBuilder(Fixture.class).properties(
                "server.address=127.0.0.1", "server.port=0", "management.server.address=127.0.0.1",
                "management.server.port=" + managementPort,
                "management.endpoints.web.base-path=/", "management.endpoints.web.exposure.include=health,prometheus",
                "management.endpoints.web.path-mapping.prometheus=metrics",
                "myscoutee.remote.localization.read-token=" + TOKEN,
                "spring.main.banner-mode=off", "logging.level.root=WARN").run();
        origin = URI.create("http://127.0.0.1:" + server.getEnvironment().getProperty("local.server.port"));
        http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        meters = new SimpleMeterRegistry();
        remote = client(origin, Duration.ofSeconds(3));
    }

    static RemoteTranslationBundles client(URI uri, Duration timeout) {
        return new RemoteTranslationBundles(uri, TOKEN, true, Duration.ofSeconds(1), timeout, new JsonMapper(), meters);
    }

    @AfterEach void clear() { MongoSessionModeContext.clear(); }
    @AfterAll static void stop() {
        if (remote != null) remote.close();
        if (http != null) http.shutdownNow();
        if (server != null) server.close();
        if (meters != null) meters.close();
    }

    @Test void realAndDemoMatchCanonicalLanguageVersionAndNullDataSemantics() throws Exception {
        var local = server.getBean(I18nService.class);
        for (var mode : List.of(MongoSessionMode.DEMO, MongoSessionMode.REAL, MongoSessionMode.DEMO, MongoSessionMode.REAL)) {
            MongoSessionModeContext.set(mode);
            var expected = local.getBundle(null, "hu-HU, en;q=0.8", null);
            var actual = remote.getBundle(null, "hu-HU, en;q=0.8", null);
            assertEquals(expected, actual);
            assertEquals("hu", actual.lang());
            assertEquals(mode.name() + " üdvözlet", actual.data().get("greeting"));
            var unchanged = remote.getBundle("hu", null, actual.version());
            assertEquals(local.getBundle("hu", null, actual.version()), unchanged);
            assertEquals(actual.version(), unchanged.version());
            assertNull(unchanged.data());
            assertEquals(200, request(LocalizationProtocol.PATH + "?lang=hu&version=" + actual.version(), TOKEN, mode.name()).statusCode());
            assertEquals(local.getBundle("zz", null, null), remote.getBundle("zz", null, null));
        }
        assertTrue(meters.get("myscoutee.remote.requests").tag("outcome", "success").timer().count() >= 12);
    }

    HttpResponse<String> request(String path, String token, String mode) throws Exception {
        var builder = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(3));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (mode != null) builder.header(LocalizationProtocol.MODE_HEADER, mode);
        var response = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (path.split("\\?", 2)[0].equals(LocalizationProtocol.PATH)) {
            var mapper = new JsonMapper();
            Files.writeString(evidence.resolve("response-" + evidenceNumber.incrementAndGet() + ".json"),
                    mapper.writeValueAsString(Map.of("path", LocalizationProtocol.PATH,
                            "status", response.statusCode(), "body", mapper.readTree(response.body()))));
        }
        return response;
    }

    @Test void callerNeedsTheReadCredentialBeforeSelectingMode() throws Exception {
        for (String token : new String[]{null, "invalid"}) {
            var response = request(LocalizationProtocol.PATH, token, "DEMO");
            assertEquals(401, response.statusCode());
            assertEquals("{\"code\":\"UNAUTHORIZED\"}", response.body());
        }
        for (String mode : new String[]{null, "", "demo", "ANY"}) {
            assertEquals(400, request(LocalizationProtocol.PATH, TOKEN, mode).statusCode());
        }
    }

    @Test void credentialDoesNotGrantOtherBusinessRoutesOrManagementPortAccess() throws Exception {
        assertEquals(403, request("/_internal/v1/localization/other", TOKEN, "REAL").statusCode());
        assertEquals(403, request("/i18n", TOKEN, "REAL").statusCode());
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + LocalizationProtocol.PATH))
                .header("Authorization", "Bearer " + TOKEN).header(LocalizationProtocol.MODE_HEADER, "REAL").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    @Test void invalidInputAndOwnerFailureHaveBoundedNonSensitiveErrors() throws Exception {
        assertEquals(400, request(LocalizationProtocol.PATH + "?lang=" + "x".repeat(33), TOKEN, "REAL").statusCode());
        var owner = server.getBean(I18nService.class);
        doThrow(new IllegalStateException("secret database details")).when(owner).getBundle("fail", null, null);
        try {
            var response = request(LocalizationProtocol.PATH + "?lang=fail", TOKEN, "REAL");
            assertEquals(503, response.statusCode());
            assertEquals("{\"code\":\"LOCALIZATION_UNAVAILABLE\"}", response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        } finally { doCallRealMethod().when(owner).getBundle("fail", null, null); }
    }

    @Test void receiverPublishesRealHttpMetricsOnlyOnManagementPort() throws Exception {
        assertEquals(200, request(LocalizationProtocol.PATH, TOKEN, "REAL").statusCode());
        var metrics = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + managementPort + "/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, metrics.statusCode());
        assertTrue(metrics.body().contains("http_server_requests_seconds_count"));
        assertTrue(metrics.body().contains(LocalizationProtocol.PATH));
        assertEquals(403, request("/metrics", TOKEN, "REAL").statusCode());
    }

    @Test void contextIsRestoredEvenWhenTheRequestChainThrows() {
        MongoSessionModeContext.set(MongoSessionMode.REAL);
        var filter = new com.raxim.myscoutee.microservices.transport.common.InternalReadCredentialFilter(TOKEN, "localization:read");
        var request = new MockHttpServletRequest("GET", LocalizationProtocol.PATH);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        request.addHeader(LocalizationProtocol.MODE_HEADER, "DEMO");
        assertThrows(ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertEquals(MongoSessionMode.DEMO, MongoSessionModeContext.currentMode());
            throw new ServletException("expected failure");
        }));
        assertEquals(MongoSessionMode.REAL, MongoSessionModeContext.currentMode());
    }

    @Test void clientRejectsPlainHttpUnlessExplicitlyEnabledForTheLab() {
        assertThrows(IllegalArgumentException.class, () -> new RemoteTranslationBundles(origin, TOKEN, false,
                Duration.ofSeconds(1), Duration.ofSeconds(3), new JsonMapper(), meters));
    }

    @Test void redirectIsNotFollowedAndARejectedRequestIsNotRetried() throws Exception {
        var calls = new AtomicInteger();
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/credential-leak");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        peer.start();
        try (var adapter = client(URI.create("http://127.0.0.1:" + peer.getAddress().getPort()), Duration.ofSeconds(1))) {
            var failure = assertThrows(RemoteTranslationBundles.RemoteReadException.class, () -> adapter.getBundle(null, null, null));
            assertEquals(302, failure.status());
            assertEquals(1, calls.get());
        } finally { peer.stop(0); }
    }

    @Test void entireResponseHasADeadlineEvenAfterHeadersArrive() throws Exception {
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 100);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try { Thread.sleep(700); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        peer.start();
        try (var adapter = client(URI.create("http://127.0.0.1:" + peer.getAddress().getPort()), Duration.ofMillis(200))) {
            var failure = assertThrows(RemoteTranslationBundles.RemoteReadException.class, () -> adapter.getBundle(null, null, null));
            assertEquals("TIMEOUT", failure.code());
        } finally { peer.stop(0); }
    }

    @Test void oversizedResponseIsCancelledBeforeItCanBeDeserialized() throws Exception {
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/", exchange -> {
            byte[] bytes = new byte[LocalizationProtocol.MAX_RESPONSE_BYTES + 1];
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });
        peer.start();
        try (var adapter = client(URI.create("http://127.0.0.1:" + peer.getAddress().getPort()), Duration.ofSeconds(3))) {
            var failure = assertThrows(RemoteTranslationBundles.RemoteReadException.class, () -> adapter.getBundle(null, null, null));
            assertEquals("UNAVAILABLE", failure.code());
        } finally { peer.stop(0); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
            "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
            "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"})
    @Import({LocalizationServerConfiguration.class, ServiceSecurityConfiguration.class})
    static class Fixture {
        @Bean com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes() {
            return mock(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService.class);
        }
        @Bean com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner databases() {
            return new com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner("real", "demo");
        }

        @Bean I18nService canonicalOwner() {
            var bundles = mock(I18nBundleRepository.class);
            var messages = mock(I18nMessageRepository.class);
            when(bundles.findAll()).thenAnswer(call -> List.of(bundle("en"), bundle("hu")));
            when(messages.findAll()).thenAnswer(call -> List.of(message("en"), message("hu")));
            return spy(new I18nService(messages, bundles));
        }
        private I18nBundle bundle(String language) {
            var bundle = new I18nBundle();
            bundle.setLang(language);
            bundle.setVersion(MongoSessionModeContext.currentMode().name() + "-1");
            return bundle;
        }
        private I18nMessage message(String language) {
            var message = new I18nMessage();
            message.setLang(language);
            message.setKey("greeting");
            message.setValue(MongoSessionModeContext.currentMode().name() + " üdvözlet");
            return message;
        }
    }
}
