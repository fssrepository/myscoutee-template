package com.raxim.myscoutee.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("standard")
class RuntimeStatusPublisherTest {
    @Test
    void publishesOwnHealthAndShutdownWithPrivateCredentialWithoutPollingReceiver() throws Exception {
        var requests = new LinkedBlockingQueue<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/runtime-status", exchange -> {
            requests.add(exchange.getRequestMethod() + "|" + exchange.getRequestHeaders().getFirst("Authorization")
                    + "|" + exchange.getRequestHeaders().getFirst("X-MyScoutee-Service") + "|"
                    + new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        var up = new AtomicBoolean(true);
        var publisher = new RuntimeStatusPublisher(up::get, "http://127.0.0.1:" + server.getAddress().getPort()
                + "/internal/runtime-status", "identity", "a".repeat(32), "");
        try {
            publisher.publish();
            String first = requests.poll(2, TimeUnit.SECONDS);
            assertThat(first).startsWith("POST|Bearer " + "a".repeat(32) + "|identity|").contains("\"ready\":true");
            up.set(false);
            publisher.publish();
            assertThat(requests.poll(2, TimeUnit.SECONDS)).contains("\"ready\":false");
            publisher.close();
            assertThat(requests.poll(2, TimeUnit.SECONDS)).contains("\"ready\":false");
            publisher.publish();
            assertThat(requests).isEmpty();
        } finally { publisher.close(); server.stop(0); }
    }

    @Test
    void missingCredentialRecoversWhenSharedComposeFileAppearsAndHealthFailureIsDown() throws Exception {
        var directory = Files.createTempDirectory("runtime-status-credential-");
        var path = directory.resolve("token");
        var requests = new LinkedBlockingQueue<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/runtime-status", exchange -> {
            requests.add(exchange.getRequestHeaders().getFirst("Authorization") + "|"
                    + new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        server.start();
        var publisher = new RuntimeStatusPublisher(() -> { throw new IllegalStateException("health unavailable"); },
                "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/runtime-status",
                "profilemanagement", "", path.toString());
        try {
            publisher.publish(); assertThat(requests).isEmpty();
            Files.writeString(path, "b".repeat(32));
            publisher.publish();
            assertThat(requests.poll(2, TimeUnit.SECONDS)).startsWith("Bearer " + "b".repeat(32)).contains("\"ready\":false");
            Files.writeString(path, "c".repeat(32));
            publisher.publish();
            assertThat(requests.poll(2, TimeUnit.SECONDS)).startsWith("Bearer " + "c".repeat(32));
        } finally { publisher.close(); server.stop(0); Files.deleteIfExists(path); Files.delete(directory); }
    }
}
