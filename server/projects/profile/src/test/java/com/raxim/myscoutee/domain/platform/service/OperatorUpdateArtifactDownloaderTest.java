package com.raxim.myscoutee.domain.platform.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@org.junit.jupiter.api.Tag("embedded")
class OperatorUpdateArtifactDownloaderTest {
    private static final String ACCEPT_HEADER =
            "application/vnd.debian.binary-package, application/octet-stream";

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyAbsoluteCredentialFreeFragmentFreeHttpsDebianUrl() {
        assertThat(OperatorUpdateArtifactDownloader.requireHttpsArtifactUri(
                "https://downloads.example.test/releases/myscoutee_1.2.3_amd64.deb"))
                .hasScheme("https")
                .hasHost("downloads.example.test");

        assertThatThrownBy(() ->
                OperatorUpdateArtifactDownloader.requireHttpsArtifactUri(
                        "http://downloads.example.test/myscoutee.deb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() ->
                OperatorUpdateArtifactDownloader.requireHttpsArtifactUri(
                        "https://user:secret@downloads.example.test/myscoutee.deb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() ->
                OperatorUpdateArtifactDownloader.requireHttpsArtifactUri(
                        "https://downloads.example.test/myscoutee.deb#fragment"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() ->
                OperatorUpdateArtifactDownloader.requireHttpsArtifactUri(
                        "https://downloads.example.test/myscoutee.zip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Debian");
    }

    @Test
    void followsBounded302And307HttpsRedirectsWithoutForwardingSensitiveHeaders()
            throws Exception {
        byte[] artifact = "signed-debian-artifact".getBytes(UTF_8);
        try (TestHttpServer server = new TestHttpServer()) {
            server.redirect(
                    "/release/myscoutee.deb",
                    302,
                    server.httpsUri("localhost", "/signed-assets/blob").toString(),
                    "release-session=must-not-be-forwarded");
            server.redirect(
                    "/signed-assets/blob",
                    307,
                    "/objects/github-release-object",
                    null);
            server.respond(
                    "/objects/github-release-object",
                    200,
                    artifact);

            Path target = temporaryDirectory.resolve("downloaded.deb");
            List<Long> progress = new ArrayList<>();
            downloader(server).download(
                    server.httpsUri("127.0.0.1", "/release/myscoutee.deb")
                            .toString(),
                    target,
                    artifact.length,
                    1024L,
                    progress::add);

            assertThat(Files.readAllBytes(target)).containsExactly(artifact);
            assertThat(progress).isNotEmpty();
            assertThat(progress.get(progress.size() - 1))
                    .isEqualTo((long) artifact.length);
            assertThat(server.requestHeader(
                    "/objects/github-release-object",
                    "Accept"))
                    .isEqualTo(ACCEPT_HEADER);
            assertThat(server.requestHeader(
                    "/objects/github-release-object",
                    "Authorization"))
                    .isNull();
            assertThat(server.requestHeader(
                    "/objects/github-release-object",
                    "Cookie"))
                    .isNull();
        }
    }

    @Test
    void rejectsRedirectWithoutLocation() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.redirect("/missing-location.deb", 302, null, null);

            assertDownloadRejected(
                    server,
                    "/missing-location.deb",
                    "missing its Location header");
        }
    }

    @Test
    void rejectsInvalidRedirectLocation() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.redirect("/invalid-location.deb", 302, "%", null);

            assertDownloadRejected(
                    server,
                    "/invalid-location.deb",
                    "Location is invalid");
        }
    }

    @Test
    void rejectsCredentialFragmentAndHttpRedirectTargets() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.redirect(
                    "/credentials.deb",
                    302,
                    "https://user:secret@localhost:"
                            + server.port()
                            + "/artifact",
                    null);
            server.redirect(
                    "/fragment.deb",
                    302,
                    server.httpsUri("localhost", "/artifact") + "#fragment",
                    null);
            server.redirect(
                    "/downgrade.deb",
                    302,
                    server.httpUri("/artifact").toString(),
                    null);

            assertDownloadRejected(
                    server,
                    "/credentials.deb",
                    "credential-free");
            assertDownloadRejected(
                    server,
                    "/fragment.deb",
                    "fragment-free");
            assertDownloadRejected(
                    server,
                    "/downgrade.deb",
                    "HTTPS");
        }
    }

    @Test
    void rejectsRedirectLoop() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            server.redirect("/loop-a.deb", 302, "/loop-b", null);
            server.redirect("/loop-b", 307, "/loop-a.deb", null);

            assertDownloadRejected(server, "/loop-a.deb", "redirect loop");
        }
    }

    @Test
    void rejectsMoreThanFiveRedirects() throws Exception {
        try (TestHttpServer server = new TestHttpServer()) {
            for (int index = 0; index <= 5; index++) {
                String path = index == 0
                        ? "/hop-0.deb"
                        : "/hop-" + index;
                server.redirect(
                        path,
                        index % 2 == 0 ? 302 : 307,
                        "/hop-" + (index + 1),
                        null);
            }

            assertDownloadRejected(
                    server,
                    "/hop-0.deb",
                    "exceeded the redirect limit");
        }
    }

    @Test
    void keepsExistingTargetUntouchedAcrossRedirects() throws Exception {
        byte[] existingContent = "existing".getBytes(UTF_8);
        byte[] artifact = "replacement".getBytes(UTF_8);
        try (TestHttpServer server = new TestHttpServer()) {
            server.redirect("/existing-target.deb", 302, "/artifact", null);
            server.respond("/artifact", 200, artifact);
            Path target = temporaryDirectory.resolve("existing.deb");
            Files.write(target, existingContent);

            assertThatThrownBy(() -> downloader(server).download(
                    server.httpsUri("127.0.0.1", "/existing-target.deb")
                            .toString(),
                    target,
                    artifact.length,
                    1024L,
                    ignored -> {
                    }))
                    .isInstanceOf(IOException.class);

            assertThat(Files.readAllBytes(target))
                    .containsExactly(existingContent);
        }
    }

    private void assertDownloadRejected(
            TestHttpServer server,
            String path,
            String message) {
        Path target = temporaryDirectory.resolve(
                path.substring(path.lastIndexOf('/') + 1) + ".target");
        assertThatThrownBy(() -> downloader(server).download(
                server.httpsUri("127.0.0.1", path).toString(),
                target,
                1L,
                1024L,
                ignored -> {
                }))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(message);
        assertThat(target).doesNotExist();
    }

    private static OperatorUpdateArtifactDownloader downloader(
            TestHttpServer server) {
        HttpClient transport = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new OperatorUpdateArtifactDownloader(
                new HttpsToHttpLoopbackClient(transport, server.port()),
                Duration.ofSeconds(5));
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final Map<String, Map<String, List<String>>> requestHeaders =
                new HashMap<>();

        private TestHttpServer() throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getLoopbackAddress(),
                            0),
                    0);
            server.start();
        }

        private int port() {
            return server.getAddress().getPort();
        }

        private URI httpsUri(String host, String path) {
            return URI.create("https://" + host + ":" + port() + path);
        }

        private URI httpUri(String path) {
            return URI.create("http://127.0.0.1:" + port() + path);
        }

        private void redirect(
                String path,
                int status,
                String location,
                String cookie) {
            register(path, exchange -> {
                if (location != null) {
                    exchange.getResponseHeaders().set("Location", location);
                }
                if (cookie != null) {
                    exchange.getResponseHeaders().set("Set-Cookie", cookie);
                }
                exchange.sendResponseHeaders(status, -1L);
                exchange.close();
            });
        }

        private void respond(
                String path,
                int status,
                byte[] body) {
            register(path, exchange -> {
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
        }

        private void register(String path, HttpHandler handler) {
            server.createContext(path, exchange -> {
                recordRequest(exchange);
                handler.handle(exchange);
            });
        }

        private synchronized void recordRequest(HttpExchange exchange) {
            Map<String, List<String>> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach(
                    (name, values) -> headers.put(name, List.copyOf(values)));
            requestHeaders.put(exchange.getRequestURI().getPath(), headers);
        }

        private synchronized String requestHeader(
                String path,
                String name) {
            Map<String, List<String>> headers =
                    requestHeaders.getOrDefault(path, Map.of());
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    /**
     * Keeps the security-relevant URI seen by the downloader as HTTPS while
     * carrying deterministic test traffic over an in-process loopback server.
     */
    private static final class HttpsToHttpLoopbackClient extends HttpClient {
        private final HttpClient delegate;
        private final int allowedPort;

        private HttpsToHttpLoopbackClient(
                HttpClient delegate,
                int allowedPort) {
            this.delegate = delegate;
            this.allowedPort = allowedPort;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return delegate.cookieHandler();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return delegate.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return delegate.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return delegate.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return delegate.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return delegate.sslParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return delegate.authenticator();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public Optional<Executor> executor() {
            return delegate.executor();
        }

        @Override
        public <T> HttpResponse<T> send(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            return delegate.send(
                    toLoopbackTransport(request),
                    responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return delegate.sendAsync(
                    toLoopbackTransport(request),
                    responseBodyHandler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return delegate.sendAsync(
                    toLoopbackTransport(request),
                    responseBodyHandler,
                    pushPromiseHandler);
        }

        private HttpRequest toLoopbackTransport(HttpRequest request) {
            URI logicalUri = request.uri();
            if (!"https".equalsIgnoreCase(logicalUri.getScheme())
                    || logicalUri.getPort() != allowedPort
                    || !isLoopbackHost(logicalUri.getHost())) {
                throw new IllegalArgumentException(
                        "The test client accepts only its logical HTTPS "
                                + "loopback endpoint.");
            }

            URI transportUri;
            try {
                transportUri = new URI(
                        "http",
                        null,
                        logicalUri.getHost(),
                        logicalUri.getPort(),
                        logicalUri.getPath(),
                        logicalUri.getQuery(),
                        null);
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException(exception);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder(transportUri);
            request.timeout().ifPresent(builder::timeout);
            request.version().ifPresent(builder::version);
            request.headers().map().forEach(
                    (name, values) -> values.forEach(
                            value -> builder.header(name, value)));
            builder.method(
                    request.method(),
                    request.bodyPublisher().orElse(
                            HttpRequest.BodyPublishers.noBody()));
            return builder.build();
        }

        private static boolean isLoopbackHost(String host) {
            return "127.0.0.1".equals(host)
                    || "localhost".equalsIgnoreCase(host);
        }
    }
}
