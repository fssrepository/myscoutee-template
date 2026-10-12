package com.raxim.myscoutee.domain.platform.service;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.platform.config.OperatorUpdateProperties;

@Component
public class OperatorUpdateArtifactDownloader {
    private static final int MAX_REDIRECTS = 5;
    private static final String ARTIFACT_ACCEPT_HEADER =
            "application/vnd.debian.binary-package, application/octet-stream";

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    @Autowired
    public OperatorUpdateArtifactDownloader(OperatorUpdateProperties properties) {
        this(
                OperatorUpdateHttpClient.create(properties),
                properties.getDownloadTimeout());
    }

    OperatorUpdateArtifactDownloader(
            HttpClient httpClient,
            Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
    }

    public void download(
            String artifactUrl,
            Path target,
            long expectedSize,
            long maximumSize,
            LongConsumer progress) throws IOException, InterruptedException {
        URI uri = requireHttpsArtifactUri(artifactUrl);
        if (expectedSize < 1L || expectedSize > maximumSize) {
            throw new IllegalArgumentException(
                    "The signed update artifact exceeds the configured download limit.");
        }

        Set<URI> visitedUris = new HashSet<>();
        visitedUris.add(canonicalRedirectUri(uri));
        int redirectCount = 0;
        HttpResponse<InputStream> response;
        while (true) {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(requestTimeout)
                    .header("Accept", ARTIFACT_ACCEPT_HEADER)
                    .build();
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (!isRedirect(response.statusCode())) {
                break;
            }

            URI nextUri;
            try (InputStream ignored = response.body()) {
                nextUri = requireSafeRedirectUri(
                        uri,
                        response.headers().firstValue("Location").orElse(null));
            }
            if (redirectCount >= MAX_REDIRECTS) {
                throw new IOException(
                        "The signed update artifact endpoint exceeded the redirect limit.");
            }
            if (!visitedUris.add(canonicalRedirectUri(nextUri))) {
                throw new IOException(
                        "The signed update artifact endpoint returned a redirect loop.");
            }
            redirectCount++;
            uri = nextUri;
        }

        if (response.statusCode() != 200) {
            try (InputStream ignored = response.body()) {
                // Close the bounded response without exposing its untrusted body.
            }
            throw new IOException(
                    "The signed update artifact endpoint returned HTTP "
                            + response.statusCode()
                            + ".");
        }
        long contentLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1L);
        if (contentLength >= 0L && contentLength != expectedSize) {
            try (InputStream ignored = response.body()) {
                // Close before rejecting the signed size mismatch.
            }
            throw new IOException(
                    "The update artifact Content-Length differs from the signed manifest.");
        }

        byte[] buffer = new byte[64 * 1024];
        long written = 0L;
        try (InputStream input = response.body();
                FileChannel output = FileChannel.open(
                        target,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                written += read;
                if (written > expectedSize || written > maximumSize) {
                    throw new IOException(
                            "The update artifact exceeded its signed size.");
                }
                ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
                while (bytes.hasRemaining()) {
                    output.write(bytes);
                }
                progress.accept(written);
            }
            output.force(true);
        }
        if (written != expectedSize) {
            throw new IOException(
                    "The update artifact size differs from the signed manifest.");
        }
    }

    static URI requireHttpsArtifactUri(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                    "The signed update artifact URL is missing.");
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "The signed update artifact URL is invalid.",
                    exception);
        }
        if (!isSafeHttpsUri(uri)) {
            throw new IllegalArgumentException(
                    "The signed update artifact URL must be an absolute HTTPS URL.");
        }
        if (uri.getPath() == null
                || !uri.getPath().toLowerCase(Locale.ROOT).endsWith(".deb")) {
            throw new IllegalArgumentException(
                    "The signed update artifact must be a Debian package.");
        }
        return uri;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static URI requireSafeRedirectUri(
            URI currentUri,
            String location) throws IOException {
        if (!StringUtils.hasText(location)) {
            throw new IOException(
                    "The signed update artifact redirect is missing its Location header.");
        }

        URI locationUri;
        try {
            locationUri = URI.create(location);
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "The signed update artifact redirect Location is invalid.",
                    exception);
        }

        URI nextUri;
        try {
            nextUri = currentUri.resolve(locationUri);
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "The signed update artifact redirect Location is invalid.",
                    exception);
        }
        if (!isSafeHttpsUri(nextUri)) {
            throw new IOException(
                    "The signed update artifact redirect must target an absolute, "
                            + "credential-free, fragment-free HTTPS URL.");
        }
        return nextUri;
    }

    private static boolean isSafeHttpsUri(URI uri) {
        return "https".equals(uri.getScheme() == null
                        ? ""
                        : uri.getScheme().toLowerCase(Locale.ROOT))
                && StringUtils.hasText(uri.getHost())
                && uri.getRawUserInfo() == null
                && uri.getRawFragment() == null
                && uri.getPort() <= 65_535;
    }

    private static URI canonicalRedirectUri(URI uri) {
        URI normalized = uri.normalize();
        int port = normalized.getPort() == 443 ? -1 : normalized.getPort();
        try {
            return new URI(
                    normalized.getScheme().toLowerCase(Locale.ROOT),
                    null,
                    normalized.getHost().toLowerCase(Locale.ROOT),
                    port,
                    normalized.getRawPath(),
                    normalized.getRawQuery(),
                    null);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "The signed update artifact URL is invalid.",
                    exception);
        }
    }
}
