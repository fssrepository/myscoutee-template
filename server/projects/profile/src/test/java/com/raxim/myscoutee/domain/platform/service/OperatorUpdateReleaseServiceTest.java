package com.raxim.myscoutee.domain.platform.service;
import com.raxim.myscoutee.domain.platform.api.ReleaseProtocol;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.raxim.myscoutee.common.config.JsonConfig;
import com.raxim.myscoutee.domain.platform.config.OperatorUpdateProperties;
import com.raxim.myscoutee.domain.platform.api.ReleaseProtocol.UpdateManifest;
import tools.jackson.databind.ObjectMapper;

@org.junit.jupiter.api.Tag("integration")
class OperatorUpdateReleaseServiceTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new JsonConfig().objectMapper();

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void discoversAndRevalidatesSignedGithubReleaseWithoutAnyRegistry(boolean local) throws Exception {
        var properties = new OperatorUpdateProperties();
        String downloadOrigin = local ? "https://127.0.0.1:18445" : "https://github.com";
        if (local) {
            properties.setReleaseApiOrigin(downloadOrigin);
            properties.setReleaseDownloadOrigin(downloadOrigin);
        }
        properties.setReleasePublicKeyPath(directory.resolve("public.der"));
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.write(properties.getReleasePublicKeyPath(), key.getPublic().getEncoded());
        String url = downloadOrigin + "/fssrepository/myscoutee/releases/download/v1.2.3/myscoutee_1.2.3_amd64.deb";
        String metadataUrl = downloadOrigin + "/fssrepository/myscoutee/releases/download/v1.2.3/myscoutee-update.json";
        var manifest = new java.util.LinkedHashMap<String, Object>();
        manifest.put("manifest_version", "1"); manifest.put("release_version", "1.2.3");
        manifest.put("channel", "stable"); manifest.put("published_at", "2026-09-16T12:00:00Z");
        manifest.put("minimum_compatible_version", "1.0.0"); manifest.put("maximum_compatible_version", "1.2.2");
        manifest.put("artifact_url", url); manifest.put("artifact_size_bytes", 123);
        manifest.put("artifact_sha256", "sha256:" + "a".repeat(64));
        manifest.put("package_signing_key_id", ReleaseProtocol.packageSigningKeyId(key.getPublic().getEncoded()));
        manifest.put("backup_required", false); manifest.put("expected_downtime_seconds", 300);
        manifest.put("status", "AVAILABLE"); manifest.put("release_notes_url", "https://github.com/fssrepository/myscoutee/releases/tag/v1.2.3");
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(key.getPrivate());
        signature.update(ReleaseProtocol.releasePackage(mapper.convertValue(manifest, UpdateManifest.class)));
        manifest.put("package_signature", Base64.getEncoder().encodeToString(signature.sign()));
        byte[] raw = mapper.writeValueAsBytes(manifest);
        Path privateKey = directory.resolve("private.pem");
        Files.writeString(privateKey, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        Path source = directory.resolve("manifest.json");
        Path signed = directory.resolve("myscoutee-update.json");
        Files.write(source, raw);
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("packaging"))) root = root.getParent();
        var signing = new ProcessBuilder("python3", root.resolve("packaging/release/sign-update-manifest.py").toString(),
                "--private-key", privateKey.toString(), "--manifest", source.toString(), "--output", signed.toString())
                .redirectErrorStream(true).start();
        String output = new String(signing.getInputStream().readAllBytes());
        assertThat(signing.waitFor()).as(output).isZero();
        byte[] envelope = Files.readAllBytes(signed);
        byte[] list = mapper.writeValueAsBytes(new Object[]{Map.of("tag_name", "v1.2.3", "draft", false, "prerelease", false,
                "assets", new Object[]{Map.of("name", "myscoutee_1.2.3_amd64.deb", "size", 123, "browser_download_url", url),
                    Map.of("name", "myscoutee-update.json", "browser_download_url", metadataUrl)})});
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            java.net.http.HttpRequest request = invocation.getArgument(0);
            HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(new ByteArrayInputStream(
                    request.uri().getPath().startsWith("/repos/") ? list : envelope));
            return response;
        });
        var service = new OperatorUpdateReleaseService(properties, mapper, client);
        var release = service.releases().items().getFirst();
        assertThat(release.updateManifest().packageSignatureVerified()).isTrue();
        assertThat(release.updateManifest().backupRequired()).isFalse();
        assertThat(service.find(release.announcementId())).isEqualTo(release);
        verify(client, times(4)).send(any(), any(HttpResponse.BodyHandler.class));

        // The release signature covers URL and compatibility as well as package identity.
        manifest.put("minimum_compatible_version", "0.0.0");
        var changed = mapper.readTree(envelope).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) changed).put("manifestBase64",
                Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(manifest)));
        assertThatThrownBy(() -> service.verify(mapper.writeValueAsBytes(changed)))
                .hasMessageContaining("metadata signature");
        Files.write(properties.getReleasePublicKeyPath(), KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        assertThatThrownBy(() -> service.verify(envelope)).hasMessageContaining("metadata signature");
        Files.delete(properties.getReleasePublicKeyPath());
        assertThatThrownBy(() -> service.verify(envelope)).hasMessageContaining("release key");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "http://localhost:18445", "https://user:secret@localhost", "https://localhost/path",
        "https://localhost?redirect=other", "https://localhost#fragment"
    })
    void rejectsUnsafeConfiguredOriginsBeforeNetworkAccess(String origin) {
        var properties = new OperatorUpdateProperties();
        properties.setReleaseApiOrigin(origin);
        HttpClient client = mock(HttpClient.class);
        assertThatThrownBy(() -> new OperatorUpdateReleaseService(properties, mapper, client).releases())
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(client);
    }

    @Test
    void postponedUpdateSelectsHighestQaVersionRatherThanPublicationOrder() throws Exception {
        var properties = new OperatorUpdateProperties();
        properties.setIncludePrereleases(true);
        properties.setReleasePublicKeyPath(directory.resolve("qa-public.der"));
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.write(properties.getReleasePublicKeyPath(), key.getPublic().getEncoded());
        var advertised = new java.util.ArrayList<Map<String, Object>>();
        for (String version : java.util.List.of("1.0.0-qa.2", "1.0.0-qa.10", "1.0.0-qa.3")) {
            String prefix = "https://github.com/fssrepository/myscoutee/releases/download/v" + version + "/";
            advertised.add(Map.of("tag_name", "v" + version, "draft", false, "prerelease", true,
                    "assets", new Object[]{Map.of("name", "myscoutee_" + version + "_amd64.deb",
                            "size", 123, "browser_download_url", prefix + "myscoutee_" + version + "_amd64.deb"),
                            Map.of("name", "myscoutee-update.json", "browser_download_url", prefix + "myscoutee-update.json")}));
        }
        var manifest = new java.util.LinkedHashMap<String, Object>();
        manifest.put("manifest_version", "1"); manifest.put("release_version", "1.0.0-qa.10");
        manifest.put("channel", "qa"); manifest.put("published_at", "2026-09-16T12:00:00Z");
        manifest.put("minimum_compatible_version", "1.0.0-qa.1"); manifest.put("maximum_compatible_version", "1.0.0-qa.9");
        manifest.put("artifact_url", "https://github.com/fssrepository/myscoutee/releases/download/v1.0.0-qa.10/myscoutee_1.0.0-qa.10_amd64.deb");
        manifest.put("artifact_size_bytes", 123); manifest.put("artifact_sha256", "sha256:" + "a".repeat(64));
        manifest.put("package_signing_key_id", ReleaseProtocol.packageSigningKeyId(key.getPublic().getEncoded()));
        manifest.put("backup_required", false); manifest.put("expected_downtime_seconds", 300); manifest.put("status", "AVAILABLE");
        var signature = Signature.getInstance("Ed25519");
        signature.initSign(key.getPrivate());
        signature.update(ReleaseProtocol.releasePackage(mapper.convertValue(manifest, UpdateManifest.class)));
        manifest.put("package_signature", Base64.getEncoder().encodeToString(signature.sign()));
        byte[] raw = mapper.writeValueAsBytes(manifest);
        signature.initSign(key.getPrivate()); signature.update(OperatorUpdateReleaseService.signedMessage(raw));
        byte[] envelope = mapper.writeValueAsBytes(Map.of("schemaVersion", 1,
                "manifestBase64", Base64.getEncoder().encodeToString(raw),
                "signature", Base64.getEncoder().encodeToString(signature.sign())));
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            java.net.http.HttpRequest request = invocation.getArgument(0);
            boolean listing = request.uri().getHost().equals("api.github.com");
            if (!listing) assertThat(request.uri().getPath()).contains("/v1.0.0-qa.10/");
            HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(new ByteArrayInputStream(listing ? mapper.writeValueAsBytes(advertised) : envelope));
            return response;
        });
        var latest = new OperatorUpdateReleaseService(properties, mapper, client).releases().items().getFirst();
        assertThat(latest.updateManifest().releaseVersion()).isEqualTo("1.0.0-qa.10");
        assertThat(latest.updateManifest().minimumCompatibleVersion()).isEqualTo("1.0.0-qa.1");
        verify(client, times(2)).send(any(), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void unsignedPublishedPackageIsNotReportedAsCurrentOrInstallable() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream(
                "[{\"tag_name\":\"v1.0.0\",\"draft\":false,\"prerelease\":false,\"assets\":[{\"name\":\"myscoutee_1.0.0_amd64.deb\"}]}]".getBytes()));
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var service = new OperatorUpdateReleaseService(new OperatorUpdateProperties(), mapper, client);
        assertThatThrownBy(service::releases).hasMessageContaining("412").hasMessageContaining("signed update metadata");
    }
}
