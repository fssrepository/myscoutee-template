package com.raxim.myscoutee.microservices.transport.common;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

/** A bounded read adapter, not a generic RPC proxy or a business-rule copy. */
public class RemoteReadTransport implements AutoCloseable {
    private final URI endpoint;
    private final String token;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;
    private final HttpClient client;
    private final Duration timeout;
    private final Semaphore permits = new Semaphore(8);

    public RemoteReadTransport(URI baseUri, String token, boolean allowInsecureHttp,
            Duration connectTimeout, Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        this(baseUri, token, allowInsecureHttp, connectTimeout, requestTimeout, mapper, metrics, Duration.ofSeconds(30));
    }

    /** Native media conversion and manual jobs can explicitly opt into a bounded larger request window. */
    protected RemoteReadTransport(URI baseUri, String token, boolean allowInsecureHttp,
            Duration connectTimeout, Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics, Duration maxRequestTimeout) {
        if (maxRequestTimeout.isNegative() || maxRequestTimeout.isZero() || maxRequestTimeout.compareTo(Duration.ofMinutes(15)) > 0)
            throw new IllegalArgumentException("Maximum request window must be positive and at most fifteen minutes");
        if (baseUri.getHost() == null || baseUri.getUserInfo() != null || baseUri.getQuery() != null
                || baseUri.getFragment() != null || !(baseUri.getPath().isEmpty() || baseUri.getPath().equals("/"))
                || !("https".equals(baseUri.getScheme()) || allowInsecureHttp && "http".equals(baseUri.getScheme()))) {
            throw new IllegalArgumentException("Internal read endpoint must be an HTTPS origin; plain HTTP requires explicit isolated-lab opt-in.");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero() || connectTimeout.compareTo(Duration.ofSeconds(10)) > 0
                || requestTimeout.isNegative() || requestTimeout.isZero() || requestTimeout.compareTo(maxRequestTimeout) > 0) {
            throw new IllegalArgumentException("Remote timeouts must be positive; connect at most 10 seconds, request within the adapter limit.");
        }
        this.endpoint = baseUri;
        this.token = InternalReadCredentialFilter.requireToken(token);
        this.mapper = mapper;
        this.metrics = metrics;
        this.timeout = requestTimeout;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    protected <T> T exchange(String path, TypeReference<T> type, String operation, int maxResponseBytes, java.util.function.Predicate<T> valid) {
        return exchange(path, null, type, operation, maxResponseBytes, valid);
    }

    /** Identifier queries retain canonical null (omitted) versus empty-string values. */
    protected String queryPath(String path, String... pairs) {
        var parameters = new java.util.ArrayList<String>();
        for (int i = 0; i < pairs.length; i += 2) {
            String value = pairs[i + 1];
            if (value == null) continue;
            if (value.length() > 512) throw new IllegalArgumentException("Internal read parameter exceeds size limit.");
            parameters.add(pairs[i] + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return path + (parameters.isEmpty() ? "" : "?" + String.join("&", parameters));
    }

    /** POST is used only for explicit read-query DTOs whose URL arrays exceed request-line limits. */
    protected <T> T exchange(String path, byte[] query, TypeReference<T> type, String operation, int maxResponseBytes, java.util.function.Predicate<T> valid) {
        return exchangeAuthorized(path, query, type, operation, maxResponseBytes, valid, java.util.Map.of());
    }

    /** Reuses the same bounded transport for explicitly signed command adapters. Never retries. */
    protected <T> T exchangeAuthorized(String path, byte[] query, TypeReference<T> type, String operation,
            int maxResponseBytes, java.util.function.Predicate<T> valid, java.util.Map<String,String> headers) {
        return exchangeRequest(request(path, query, "application/json", headers), operation, maxResponseBytes,
                response -> decodeResponse(response, type, valid));
    }

    private <T> T decodeResponse(HttpResponse<byte[]> response, TypeReference<T> type,
            java.util.function.Predicate<T> valid) {
            if (response.statusCode() != 200) {
                String remoteCode = null;
                if (response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                    try {
                        var error = mapper.readTree(response.body());
                        if (error.isObject() && error.size() == 1 && error.has("code") && error.get("code").isString())
                            remoteCode = error.get("code").asString();
                    } catch (RuntimeException invalidError) {
                        // A gateway/proxy error must never be interpreted as domain absence.
                    }
                }
                throw new RemoteReadException("HTTP_ERROR", response.statusCode(), remoteCode);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                throw new RemoteReadException("INVALID_RESPONSE", 200);
            }
            T result;
            try { result = mapper.readValue(response.body(), type); }
            catch (RuntimeException invalid) { throw new RemoteReadException("INVALID_RESPONSE", 200); }
            if (result == null || !valid.test(result)) throw new RemoteReadException("INVALID_RESPONSE", 200);
            return result;
    }

    /** Same concurrency, whole-body deadline and byte bound for an explicit HTTP adapter. */
    protected <T> T exchangeRequest(HttpRequest request, String operation, int maxResponseBytes,
            java.util.function.Function<HttpResponse<byte[]>, T> decode) {
        Timer.Sample sample = Timer.start(metrics);
        String outcome = "failure";
        boolean acquired = permits.tryAcquire();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            if (!acquired) throw new RemoteReadException("BUSY", 0);
            pending = client.sendAsync(request, info -> new LimitedBody(maxResponseBytes));
            // Covers the whole body, including a server that sends headers then stalls.
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            T result = decode.apply(response);
            outcome = "success";
            return result;
        } catch (TimeoutException timeoutFailure) {
            outcome = "timeout";
            throw new RemoteReadException("TIMEOUT", 0);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            outcome = "interrupted";
            throw new RemoteReadException("INTERRUPTED", 0);
        } catch (ExecutionException failure) {
            outcome = failure.getCause() instanceof java.net.http.HttpTimeoutException ? "timeout" : "unavailable";
            throw new RemoteReadException(outcome.equals("timeout") ? "TIMEOUT" : "UNAVAILABLE", 0);
        } catch (RemoteReadException failure) {
            outcome = failure.code().equals("BUSY") ? "busy" : "rejected";
            throw failure;
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            if (acquired) permits.release();
            sample.stop(metrics.timer("myscoutee.remote.requests", "operation", operation, "outcome", outcome));
        }
    }

    private HttpRequest request(String path, byte[] query, String accept, java.util.Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(endpoint.resolve(path)).timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .header(InternalReadCredentialFilter.MODE_HEADER, MongoSessionModeContext.currentMode().name())
                .header("Accept", accept);
        headers.forEach(builder::header);
        return query == null ? builder.GET().build()
                : builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(query)).build();
    }

    /** Native job progress is delivered on the calling thread, retaining its REAL/DEMO and job context. */
    protected void exchangeLines(String path, byte[] query, String operation, int maxFrameBytes,
            int maxResponseBytes, java.util.function.Consumer<byte[]> consume) {
        try (var stream = openLines(path, query, operation, maxFrameBytes, maxResponseBytes)) {
            byte[] frame;
            while ((frame = stream.next()) != null) consume.accept(frame);
        }
    }

    /** The caller owns this bounded stream and must close it, including early termination. */
    protected LineExchange openLines(String path, byte[] query, String operation, int maxFrameBytes, int maxResponseBytes) {
        return new LineExchange(path, query, operation, maxFrameBytes, maxResponseBytes);
    }

    protected final class LineExchange implements AutoCloseable {
        private final Timer.Sample sample = Timer.start(metrics);
        private final String operation;
        private final LimitedLines body;
        private final long deadline = System.nanoTime() + timeout.toNanos();
        private CompletableFuture<HttpResponse<LimitedLines>> pending;
        private boolean acquired, closed, complete;
        private String outcome = "cancelled";
        private LineExchange(String path, byte[] query, String operation, int maxFrameBytes, int maxResponseBytes) {
            this.operation = operation;
            this.body = new LimitedLines(maxFrameBytes, maxResponseBytes);
            try {
                acquired = permits.tryAcquire();
                if (!acquired) throw new RemoteReadException("BUSY", 0);
                pending = client.sendAsync(request(path, query, "application/x-ndjson", java.util.Map.of()), info -> body);
                var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (response.statusCode() != 200) throw new RemoteReadException("HTTP_ERROR", response.statusCode());
                if (!response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim()
                        .equalsIgnoreCase("application/x-ndjson")) throw new RemoteReadException("INVALID_RESPONSE", 200);
            } catch (TimeoutException error) { fail("timeout"); throw new RemoteReadException("TIMEOUT", 0); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); fail("interrupted"); throw new RemoteReadException("INTERRUPTED", 0); }
            catch (ExecutionException error) { fail("unavailable"); throw new RemoteReadException("UNAVAILABLE", 0); }
            catch (RuntimeException error) { fail("rejected"); throw error; }
        }
        /** Null means HTTP EOF; application protocols must still verify their final receipt. */
        public byte[] next() {
            if (complete) return null;
            if (closed) throw new IllegalStateException("Stream already closed");
            try {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException();
                var item = body.next(remaining);
                if (item == null) throw new TimeoutException();
                if (item.failure() != null) throw new RemoteReadException("INVALID_RESPONSE", 200);
                if (item.complete()) { complete = true; outcome = "success"; close(); return null; }
                return item.bytes();
            } catch (TimeoutException error) { fail("timeout"); throw new RemoteReadException("TIMEOUT", 0); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); fail("interrupted"); throw new RemoteReadException("INTERRUPTED", 0); }
            catch (RuntimeException error) { fail("rejected"); throw error; }
        }
        private void fail(String reason) { outcome = reason; close(); }
        @Override public void close() {
            if (closed) return;
            closed = true; body.cancel();
            if (pending != null && !pending.isDone()) pending.cancel(true);
            if (acquired) permits.release();
            sample.stop(metrics.timer("myscoutee.remote.requests", "operation", operation, "outcome", outcome));
        }
    }

    @Override public void close() { client.shutdownNow(); }

    public static final class RemoteReadException extends RuntimeException {
        private final String code;
        private final int status;
        private final String remoteCode;
        public RemoteReadException(String code, int status) {
            this(code, status, null);
        }
        public RemoteReadException(String code, int status, String remoteCode) {
            super("Internal remote read failed: " + code + " (HTTP " + status + ")");
            this.code = code;
            this.status = status;
            this.remoteCode = remoteCode;
        }
        public String code() { return code; }
        public int status() { return status; }
        public String remoteCode() { return remoteCode; }
    }

    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                if (item.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IllegalStateException("Remote response exceeds size limit."));
                    return;
                }
                byte[] block = new byte[item.remaining()];
                item.get(block);
                bytes.writeBytes(block);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
