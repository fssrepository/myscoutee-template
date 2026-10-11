package com.raxim.myscoutee.domain.platform.repository;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.raxim.myscoutee.domain.platform.config.OperatorTlsProperties;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsConfigurationDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsConfigurationUpdateDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsJobDto;

import tools.jackson.databind.ObjectMapper;

@Repository
public class OperatorTlsRepository {
    private static final int MAX_DOCUMENT_BYTES = 256 * 1024;

    private final OperatorTlsProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public OperatorTlsRepository(
            OperatorTlsProperties properties,
            ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC(), new SecureRandom());
    }

    OperatorTlsRepository(
            OperatorTlsProperties properties,
            ObjectMapper objectMapper,
            Clock clock,
            SecureRandom secureRandom) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public Optional<TlsConfigurationDto> readConfiguration() throws IOException {
        return read(
                stateDir().resolve("runtime/configuration.json"),
                TlsConfigurationDto.class);
    }

    public Optional<TlsJobDto> readJob(String jobId) throws IOException {
        return read(
                stateDir().resolve("runtime/jobs")
                        .resolve(jobId)
                        .resolve("status.json"),
                TlsJobDto.class);
    }

    public boolean queued(String jobId) {
        return safeRegularFile(
                stateDir().resolve("inbox/requests").resolve(jobId + ".json"));
    }

    public TlsJobDto enqueue(
            String action,
            TlsConfigurationUpdateDto configuration,
            String actor) throws IOException {
        Path requestDir = stateDir().resolve("inbox/requests");
        requireSafeDirectory(requestDir);
        String jobId = nextJobId();
        String requestedAt = Instant.now(clock).toString();
        Path target = requestDir.resolve(jobId + ".json");
        Path temporary = requestDir.resolve("." + jobId + ".tmp");
        Map<String, Object> payload = Map.of(
                "schemaVersion", 1,
                "jobId", jobId,
                "action", action,
                "requestedAt", requestedAt,
                "requestedBy", actor,
                "configuration", Map.of(
                        "enabled", configuration.enabled(),
                        "mode", configuration.mode().name(),
                        "domain", safeText(configuration.domain()),
                        "contactEmail", safeText(configuration.contactEmail()),
                        "autoRenew", configuration.autoRenew(),
                        "certificate", safeText(configuration.certificate()),
                        "privateKey", safeText(configuration.privateKey())));
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            try {
                Files.setPosixFilePermissions(
                        temporary,
                        PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Production is POSIX; this keeps repository tests portable.
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            fsync(requestDir);
        } catch (IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        return new TlsJobDto(
                jobId,
                com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsJobPhase.QUEUED,
                0,
                "operator.configuration.tls.job.queued",
                requestedAt,
                null);
    }

    public Path stateDir() {
        Path state = properties.getStateDir().toAbsolutePath().normalize();
        if (state.getNameCount() < 2) {
            throw new IllegalStateException("TLS state directory is unsafe.");
        }
        return state;
    }

    private <T> Optional<T> read(Path path, Class<T> type) throws IOException {
        if (!safeRegularFile(path)) {
            return Optional.empty();
        }
        if (Files.size(path) > MAX_DOCUMENT_BYTES) {
            throw new IOException("TLS state document is too large.");
        }
        return Optional.of(objectMapper.readValue(path.toFile(), type));
    }

    private static boolean safeRegularFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }

    private static void requireSafeDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("TLS request directory is unavailable.");
        }
    }

    private String nextJobId() {
        byte[] value = new byte[16];
        secureRandom.nextBytes(value);
        return "tls_" + HexFormat.of().formatHex(value);
    }

    private static void fsync(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
