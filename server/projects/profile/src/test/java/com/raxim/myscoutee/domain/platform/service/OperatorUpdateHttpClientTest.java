package com.raxim.myscoutee.domain.platform.service;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateArtifactDownloader;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.raxim.myscoutee.domain.platform.config.OperatorUpdateProperties;

@org.junit.jupiter.api.Tag("embedded")
class OperatorUpdateHttpClientTest {
    @TempDir Path directory;

    @Test
    void blankCertificateConfigurationUsesDefaultTrust() {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("operator.update.tls-ca-certificate", ""));
        var props = new org.springframework.boot.context.properties.bind.Binder(source)
                .bind("operator.update", OperatorUpdateProperties.class).orElseGet(OperatorUpdateProperties::new);
        assertThat(props.getTlsCaCertificate()).isNull();
        assertThat(OperatorUpdateHttpClient.create(props)).isNotNull();
    }

    @Test
    void downloadsOverVerifiedLocalTlsAndRejectsUntrustedCertificate() throws Exception {
        Path storePath = directory.resolve("server.p12");
        Path certPath = directory.resolve("server.pem");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        run(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-keystore", storePath.toString(), "-storetype", "PKCS12", "-storepass", "fixture-password",
                "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1,dns:localhost", "-validity", "2");
        run(keytool, "-exportcert", "-rfc", "-alias", "server", "-keystore", storePath.toString(),
                "-storepass", "fixture-password", "-file", certPath.toString());
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(storePath)) { keys.load(input, "fixture-password".toCharArray()); }
        var manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(keys, "fixture-password".toCharArray());
        var tls = SSLContext.getInstance("TLS");
        tls.init(manager.getKeyManagers(), null, null);
        var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        byte[] bytes = "signed-package-transport-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/package.deb", exchange -> {
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            String url = "https://127.0.0.1:" + server.getAddress().getPort() + "/package.deb";
            var properties = new OperatorUpdateProperties();
            properties.setDownloadTimeout(Duration.ofSeconds(5));
            var untrusted = new OperatorUpdateArtifactDownloader(properties);
            assertThatThrownBy(() -> untrusted.download(url, directory.resolve("untrusted.deb"), bytes.length, 4096, n -> {}))
                    .isInstanceOf(javax.net.ssl.SSLHandshakeException.class);
            properties.setTlsCaCertificate(certPath);
            Path target = directory.resolve("verified.deb");
            new OperatorUpdateArtifactDownloader(properties).download(url, target, bytes.length, 4096, n -> {});
            assertThat(Files.readAllBytes(target)).isEqualTo(bytes);
        } finally { server.stop(0); }
    }

    private static void run(String... command) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
    }
}
