package com.raxim.myscoutee.common.runtime;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Publishes this process's health to the gateway; never asks another backend. */
@Component
@ConditionalOnProperty(name = "myscoutee.runtime-status.enabled", havingValue = "true")
public class RuntimeStatusPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(RuntimeStatusPublisher.class);
    private final BooleanSupplier healthy;
    private final URI url;
    private final String service;
    private final String token;
    private final String tokenFile;
    private final String instance = UUID.randomUUID().toString();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "runtime-status-publisher");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closing;
    private boolean started;
    private boolean unavailable;

    @Autowired
    public RuntimeStatusPublisher(HealthEndpoint health,
            @Value("${MYSCOUTEE_RUNTIME_STATUS_URL:}") String url,
            @Value("${MYSCOUTEE_RUNTIME_STATUS_SERVICE:profilemanagement}") String service,
            @Value("${MYSCOUTEE_RUNTIME_STATUS_TOKEN:}") String token,
            @Value("${MYSCOUTEE_RUNTIME_STATUS_TOKEN_FILE:}") String tokenFile) {
        this(() -> Status.UP.equals(health.health().getStatus()), url, service, token, tokenFile);
    }

    RuntimeStatusPublisher(BooleanSupplier healthy, String url, String service, String token, String tokenFile) {
        this.healthy = healthy;
        this.url = URI.create(url);
        if (!java.util.Set.of("http", "https").contains(this.url.getScheme())
                || this.url.getHost() == null || this.url.getUserInfo() != null || this.url.getFragment() != null) {
            throw new IllegalArgumentException("Invalid runtime-status receiver URL");
        }
        if (!service.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid runtime-status service");
        this.service = service;
        this.token = token;
        this.tokenFile = tokenFile;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (started || closing) return;
        started = true;
        // A blocked health indicator cannot occupy the application's job scheduler.
        executor.scheduleWithFixedDelay(this::publish, 0, 5, TimeUnit.SECONDS);
    }

    void publish() {
        if (closing) return;
        boolean ready;
        try { ready = healthy.getAsBoolean(); }
        catch (RuntimeException error) { ready = false; }
        send(ready);
    }

    private synchronized void send(boolean ready) {
        try {
            String credential = token.isBlank() && !tokenFile.isBlank()
                    ? Files.readString(Path.of(tokenFile)).trim() : token.trim();
            if (credential.length() < 32) throw new IllegalStateException("Missing runtime-status credential");
            String body = "{\"instance\":\"" + instance + "\",\"ready\":" + (ready && !closing) + "}";
            var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(1))
                    .header("Content-Type", "application/json")
                    .header("X-MyScoutee-Service", service)
                    .header("Authorization", "Bearer " + credential)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status != 204) throw new IllegalStateException("Runtime-status publication rejected");
            if (unavailable) LOG.info("Runtime-status publication recovered.");
            unavailable = false;
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!unavailable && !closing) LOG.warn("Runtime-status receiver unavailable; its readiness lease will expire.");
            unavailable = true;
        }
    }

    @PreDestroy
    public void close() {
        if (closing) return;
        closing = true;
        executor.shutdownNow();
        send(false);
        client.shutdownNow();
    }
}
