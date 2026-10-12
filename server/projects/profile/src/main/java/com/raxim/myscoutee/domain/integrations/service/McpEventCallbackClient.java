package com.raxim.myscoutee.domain.integrations.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/** HTTPS transport for the Events protocol. DNS is checked by the actual connection resolver. */
@Component
public class McpEventCallbackClient {
    public static class Throttled extends IOException { }
    private final int requestsPerSecond;
    private final int burst;
    private double permits;
    private long refilledAt = System.nanoTime();
    private synchronized boolean ratePermit() {
        long now = System.nanoTime();
        permits = Math.min(burst, permits + (now - refilledAt) / 1_000_000_000d * requestsPerSecond);
        refilledAt = now;
        if (permits < 1) return false;
        permits -= 1;
        return true;
    }
    private final Semaphore inFlight;
    private final Semaphore verifications = new Semaphore(2);
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("mcp-events-deadlines").factory());
    private final CloseableHttpClient http;

    public McpEventCallbackClient(
            @org.springframework.beans.factory.annotation.Value("${integration.events.requests-per-second:4}") int requestsPerSecond,
            @org.springframework.beans.factory.annotation.Value("${integration.events.burst:4}") int burst,
            @org.springframework.beans.factory.annotation.Value("${integration.events.max-concurrent:8}") int maxConcurrent) {
        this.requestsPerSecond = Math.max(1, Math.min(100, requestsPerSecond));
        this.burst = Math.max(1, Math.min(100, burst));
        this.permits = this.burst;
        int concurrent = Math.max(1, Math.min(32, maxConcurrent));
        this.inFlight = new Semaphore(concurrent);
        this.http = HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                    .setMaxConnTotal(concurrent).setMaxConnPerRoute(Math.min(4, concurrent))
                    .setDnsResolver(new DnsResolver() {
                        @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                            InetAddress[] addresses = InetAddress.getAllByName(host);
                            if (addresses.length == 0) throw new UnknownHostException("Callback address unavailable");
                            for (var address : addresses) if (!publicAddress(address))
                                throw new UnknownHostException("Callback address is not public");
                            return addresses;
                        }
                        @Override public String resolveCanonicalHostname(String host) { return host; }
                    })
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(5)).setSocketTimeout(Timeout.ofSeconds(10)).build())
                    .build())
            .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(Timeout.ofSeconds(1))
                    .setResponseTimeout(Timeout.ofSeconds(10)).build())
            // Revalidate DNS on every delivery, retaining the URL hostname for TLS/SNI.
            .setConnectionReuseStrategy((request, response, context) -> false)
            .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
            .disableContentCompression().build();
    }

    public record Response(int status, byte[] body) { }

    /** Reserve before claiming Mongo work so throttling does not churn queue rows or authorization queries. */
    public DeliveryPermit reserveDelivery() {
        if (!inFlight.tryAcquire()) return null;
        if (!ratePermit()) { inFlight.release(); return null; }
        return new DeliveryPermit();
    }
    public final class DeliveryPermit implements AutoCloseable {
        private boolean closed;
        private boolean used;
        private DeliveryPermit() { }
        private synchronized boolean use() {
            if (closed || used) return false;
            used = true; return true;
        }
        @Override public synchronized void close() {
            if (!closed) { closed = true; inFlight.release(); }
        }
    }

    public Response send(String url, String subscriptionId, String messageId, byte[] body,
            String secret, String previousSecret, boolean verification) throws IOException {
        return send(url, subscriptionId, messageId, body, secret, previousSecret, verification, null);
    }
    public Response send(String url, String subscriptionId, String messageId, byte[] body,
            String secret, String previousSecret, boolean verification, DeliveryPermit permit) throws IOException {
        URI uri = callbackUri(url);
        if (body.length > 262144) throw new IOException("payload_too_large");
        if (permit == null) {
            if (!ratePermit()) throw new Throttled();
            if (verification && !verifications.tryAcquire()) throw new Throttled();
            if (!inFlight.tryAcquire()) {
                if (verification) verifications.release();
                throw new Throttled();
            }
        } else if (verification || !permit.use()) {
            throw new IOException("invalid_delivery_permit");
        }
        var request = new HttpPost(uri);
        try {
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String signature = sign(secret, messageId, timestamp, body);
            if (previousSecret != null) signature += " " + sign(previousSecret, messageId, timestamp, body);
            request.setHeader("webhook-id", messageId);
            request.setHeader("webhook-timestamp", timestamp);
            request.setHeader("webhook-signature", signature);
            request.setHeader("X-MCP-Subscription-Id", subscriptionId);
            request.setHeader("Accept", "application/json");
            request.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
            // Bounds the full response, including slow streaming bodies; socket timeout alone does not.
            var deadline = deadlines.schedule(request::cancel, 10, TimeUnit.SECONDS);
            try {
                return http.execute(request, response -> {
                    byte[] bytes = response.getEntity() == null ? new byte[0]
                            : response.getEntity().getContent().readNBytes(4097);
                    if (bytes.length > 4096) throw new IOException("response_too_large");
                    return new Response(response.getCode(), bytes);
                });
            } finally { deadline.cancel(false); }
        } finally {
            if (permit == null) {
                inFlight.release();
                if (verification) verifications.release();
            }
        }
    }

    static URI callbackUri(String value) {
        try {
            if (value == null || value.length() > 2048) throw new IllegalArgumentException();
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443))
                throw new IllegalArgumentException();
            String host = uri.getHost();
            if (host.contains(":") || host.matches("[0-9.]+")) {
                try { if (!publicAddress(InetAddress.getByName(host))) throw new IllegalArgumentException(); }
                catch (UnknownHostException error) { throw new IllegalArgumentException(); }
            }
            return uri;
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("invalid_callback_url"); }
    }

    static byte[] key(String secret) {
        try {
            if (secret == null || !secret.startsWith("whsec_") || secret.length() > 96) throw new IllegalArgumentException();
            byte[] key = Base64.getDecoder().decode(secret.substring(6));
            if (key.length < 24 || key.length > 64) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("invalid_signing_secret"); }
    }

    static String sign(String secret, String id, String timestamp, byte[] body) {
        byte[] key = key(secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException("signing_unavailable"); }
        finally { java.util.Arrays.fill(key, (byte) 0); }
    }

    static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        int a = b[0] & 255, second = b[1] & 255;
        if (b.length == 4) return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && second >= 64 && second <= 127)
                && !(a == 169 && second == 254) && !(a == 172 && second >= 16 && second <= 31)
                && !(a == 192 && (second == 168 || second == 0 || (second == 88 && (b[2] & 255) == 99)))
                && !(a == 198 && (second == 18 || second == 19 || (second == 51 && (b[2] & 255) == 100)))
                && !(a == 203 && second == 0 && (b[2] & 255) == 113);
        // Global unicast only; exclude IETF special-purpose, documentation, 6to4 and ULA.
        return (a & 0xe0) == 0x20
                && !(a == 0x20 && second == 1 && ((b[2] & 255) < 2 || (b[2] == 0x0d && (b[3] & 255) == 0xb8)))
                && !(a == 0x20 && second == 2)
                && !(a == 0x3f && second == 0xff && (b[2] & 0xf0) == 0);
    }

    @PreDestroy void close() throws IOException { deadlines.shutdownNow(); http.close(); }
}
