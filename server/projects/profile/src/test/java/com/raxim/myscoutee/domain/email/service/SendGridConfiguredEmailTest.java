package com.raxim.myscoutee.domain.email.service;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.raxim.myscoutee.domain.email.config.EmailProperties;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("embedded")
class SendGridConfiguredEmailTest {
    @Test void sendsConfiguredCredentialAndPayloadToStubWithoutChangingEnvironmentCredential() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var authorization = new AtomicReference<String>(); var body = new AtomicReference<String>();
        server.createContext("/v3/mail/send", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization")); body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("X-Message-Id", "stub-accepted"); exchange.sendResponseHeaders(202, -1); exchange.close();
        }); server.start();
        try {
            var properties = new EmailProperties(); properties.getSendgrid().setApiKey("environment-key");
            properties.getSendgrid().setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/mail/send");
            var mapper = new ObjectMapper(); var provider = new SendGridEmailProvider(properties, mapper);
            var result = provider.sendConfigured(new EmailSendRequest("recipient@example.test", "Viewer", "Deletion reminder", "", "Five days remain.",
                    "sender@example.test", "MyScoutee", "sender@example.test", "account-deletion-warning", Map.of("deletionEpisode", "episode-1")), "operator-key");
            assertTrue(result.accepted()); assertEquals("stub-accepted", result.providerMessageId()); assertEquals("Bearer operator-key", authorization.get());
            var json = mapper.readTree(body.get()); assertEquals("sender@example.test", json.path("from").path("email").asText());
            assertEquals("recipient@example.test", json.path("personalizations").get(0).path("to").get(0).path("email").asText());
            assertEquals("episode-1", json.path("personalizations").get(0).path("custom_args").path("deletionEpisode").asText());
            assertEquals("environment-key", properties.getSendgrid().getApiKey());
        } finally { server.stop(0); }
    }
    @Test void invalidProviderResponseIsNotReportedAsDelivered() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); }); server.start();
        try {
            var config = new EmailProperties(); config.getSendgrid().setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            var provider = new SendGridEmailProvider(config, new ObjectMapper());
            assertFalse(provider.sendConfigured(new EmailSendRequest("recipient@example.test", "", "Subject", "", "Body", "sender@example.test", "", "sender@example.test", "warning", Map.of()), "bad-key").accepted());
        } finally { server.stop(0); }
    }
    @Test void lostResponseAfterProviderReadIsUnknown() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var received = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            received.incrementAndGet();
            exchange.close();
        });
        server.start();
        try {
            var config = new EmailProperties();
            config.getSendgrid().setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            var provider = new SendGridEmailProvider(config, new ObjectMapper());
            var result = provider.sendConfigured(new EmailSendRequest("recipient@example.test", "", "Subject", "", "Body",
                    "sender@example.test", "", "sender@example.test", "warning", Map.of()), "stub-key");
            assertFalse(result.accepted());
            assertTrue(result.outcomeUnknown());
            assertNull(result.providerMessageId());
            assertEquals(1, received.get());
        } finally { server.stop(0); }
    }
    @Test void rateLimitIsRetryableAndPreservesProviderDelay() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Retry-After", "120");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        try {
            var config = new EmailProperties();
            config.getSendgrid().setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
            var result = new SendGridEmailProvider(config, new ObjectMapper()).sendConfigured(
                    new EmailSendRequest("recipient@example.test", "", "Subject", "", "Body", "sender@example.test", "",
                            "sender@example.test", "warning", Map.of()), "stub-key");
            assertFalse(result.accepted());
            assertFalse(result.outcomeUnknown());
            assertTrue(result.retryable());
            assertEquals(120, result.retryAfterSeconds());
        } finally { server.stop(0); }
    }
}
