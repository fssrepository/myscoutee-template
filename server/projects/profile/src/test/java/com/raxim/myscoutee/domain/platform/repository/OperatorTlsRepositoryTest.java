package com.raxim.myscoutee.domain.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.raxim.myscoutee.common.config.JsonConfig;
import com.raxim.myscoutee.domain.platform.config.OperatorTlsProperties;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsCertificateMode;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsConfigurationUpdateDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsJobDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsJobPhase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@org.junit.jupiter.api.Tag("standard")
class OperatorTlsRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void springUsesTheRuntimeConstructorWhenTestDependenciesAlsoExist() {
        ObjectMapper objectMapper = new JsonConfig().objectMapper();

        new ApplicationContextRunner()
                .withBean(OperatorTlsProperties.class, OperatorTlsProperties::new)
                .withBean(ObjectMapper.class, () -> objectMapper)
                .withBean(OperatorTlsRepository.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(OperatorTlsRepository.class));
    }

    @Test
    void handsWriteOnlyCertificateMaterialToTheFixedWorkerInbox()
            throws Exception {
        Path state = temporaryDirectory.resolve("tls");
        Files.createDirectories(state.resolve("inbox/requests"));
        OperatorTlsProperties properties = new OperatorTlsProperties();
        properties.setStateDir(state);
        ObjectMapper objectMapper = new JsonConfig().objectMapper();
        OperatorTlsRepository repository =
                new OperatorTlsRepository(properties, objectMapper);
        TlsConfigurationUpdateDto request = new TlsConfigurationUpdateDto(
                true,
                TlsCertificateMode.MANUAL,
                "app.example.test",
                "",
                false,
                "-----BEGIN CERTIFICATE-----\ncertificate-data",
                "-----BEGIN PRIVATE KEY-----\nprivate-key-data");

        TlsJobDto job = repository.enqueue(
                "TEST_CERTIFICATE",
                request,
                "operator-test");

        assertThat(job.phase()).isEqualTo(TlsJobPhase.QUEUED);
        assertThat(job.jobId()).matches("^tls_[0-9a-f]{32}$");
        Path handoff =
                state.resolve("inbox/requests").resolve(job.jobId() + ".json");
        JsonNode payload = objectMapper.readTree(handoff.toFile());
        assertThat(payload.path("action").asText())
                .isEqualTo("TEST_CERTIFICATE");
        assertThat(payload.path("configuration").path("certificate").asText())
                .contains("certificate-data");
        assertThat(payload.path("configuration").path("privateKey").asText())
                .contains("private-key-data");
        assertThat(request.toString())
                .doesNotContain("certificate-data", "private-key-data");
    }
}
