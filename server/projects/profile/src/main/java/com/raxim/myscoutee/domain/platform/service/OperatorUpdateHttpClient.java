package com.raxim.myscoutee.domain.platform.service;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import com.raxim.myscoutee.domain.platform.config.OperatorUpdateProperties;

/** TLS configuration shared by release discovery and package download. */
final class OperatorUpdateHttpClient {
    private OperatorUpdateHttpClient() {}

    static HttpClient create(OperatorUpdateProperties properties) {
        var builder = HttpClient.newBuilder()
                .connectTimeout(properties.getDownloadConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        var certificate = properties.getTlsCaCertificate();
        if (certificate != null) {
            try {
                if (!Files.isRegularFile(certificate, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(certificate) > 65536) {
                    throw new IllegalArgumentException("Invalid release TLS CA file.");
                }
                var store = KeyStore.getInstance(KeyStore.getDefaultType());
                store.load(null, null);
                try (var input = Files.newInputStream(certificate)) {
                    var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
                    if (certificates.isEmpty()) throw new IllegalArgumentException("Empty release TLS CA file.");
                    int index = 0;
                    for (var trusted : certificates) store.setCertificateEntry("release-" + index++, trusted);
                }
                var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trust.init(store);
                var context = SSLContext.getInstance("TLS");
                context.init(null, trust.getTrustManagers(), null);
                builder.sslContext(context);
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot load configured release TLS trust.", exception);
            }
        }
        return builder.build();
    }
}
