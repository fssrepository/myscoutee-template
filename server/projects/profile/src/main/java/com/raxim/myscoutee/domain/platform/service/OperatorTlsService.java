package com.raxim.myscoutee.domain.platform.service;

import com.raxim.myscoutee.domain.platform.mapper.OperatorTlsMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.config.OperatorTlsProperties;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsCertificateMode;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsConfigurationDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsConfigurationUpdateDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsJobDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsJobPhase;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsTestRequestDto;
import com.raxim.myscoutee.domain.platform.repository.OperatorTlsRepository;

@Service
public class OperatorTlsService {
    private static final Pattern JOB_ID = Pattern.compile("^tls_[0-9a-f]{32}$");
    private static final Pattern DOMAIN = Pattern.compile(
            "^(?=.{1,253}$)(?![.-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$",
            Pattern.CASE_INSENSITIVE);
    private final OperatorTlsProperties properties;
    private final OperatorTlsRepository repository;
    private final OperatorTlsMapper mapper;
    private final Clock clock;

    @Autowired
    public OperatorTlsService(
            OperatorTlsProperties properties,
            OperatorTlsRepository repository,
            OperatorTlsMapper mapper) {
        this(properties, repository, mapper, Clock.systemUTC());
    }

    OperatorTlsService(
            OperatorTlsProperties properties,
            OperatorTlsRepository repository,
            OperatorTlsMapper mapper,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties);
        this.repository = Objects.requireNonNull(repository);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public TlsConfigurationDto configuration() {
        if (!available()) {
            return mapper.unavailable();
        }
        try {
            return mapper.available(repository.readConfiguration().orElse(null));
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The host TLS configuration cannot be read.",
                    exception);
        }
    }

    public TlsJobDto apply(
            TlsConfigurationUpdateDto request,
            String actorUserId) {
        return enqueue("APPLY", request, actorUserId);
    }

    public TlsJobDto test(
            TlsTestRequestDto request,
            String actorUserId) {
        if (request == null || request.kind() == null) {
            throw badRequest("A TLS test kind is required.");
        }
        return enqueue(
                request.kind().name().equals("DOMAIN")
                        ? "TEST_DOMAIN"
                        : "TEST_CERTIFICATE",
                request.configuration(),
                actorUserId);
    }

    public TlsJobDto status(String suppliedJobId) {
        requireAvailable();
        String jobId = safeText(suppliedJobId);
        if (!JOB_ID.matcher(jobId).matches()) {
            throw badRequest("The TLS job ID is invalid.");
        }
        try {
            TlsJobDto value = repository.readJob(jobId).orElse(null);
            if (value == null && repository.queued(jobId)) {
                return new TlsJobDto(
                        jobId,
                        TlsJobPhase.QUEUED,
                        0,
                        "operator.configuration.tls.job.queued",
                        Instant.now(clock).toString(),
                        null);
            }
            if (value == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The TLS job was not found.");
            }
            if (!jobId.equals(value.jobId())) {
                throw new IOException("TLS status job ID does not match.");
            }
            return value;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The TLS job status cannot be read.",
                    exception);
        }
    }

    private TlsJobDto enqueue(
            String action,
            TlsConfigurationUpdateDto request,
            String actorUserId) {
        requireAvailable();
        validate(request, action);
        String actor = safeText(actorUserId);
        if (!StringUtils.hasText(actor)
                || actor.length() > 160
                || actor.indexOf('\n') >= 0
                || actor.indexOf('\r') >= 0) {
            throw badRequest("The operator identity is invalid.");
        }
        try {
            return repository.enqueue(action, request, actor);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The host TLS worker is unavailable.",
                    exception);
        }
    }

    private void validate(TlsConfigurationUpdateDto request, String action) {
        if (request == null || request.mode() == null) {
            throw badRequest("A TLS configuration is required.");
        }
        if (!request.enabled() && !"APPLY".equals(action)) {
            throw badRequest("Enable managed HTTPS before testing it.");
        }
        String domain = safeText(request.domain()).toLowerCase();
        if (request.enabled() && !DOMAIN.matcher(domain).matches()) {
            throw badRequest("Enter a valid public DNS domain.");
        }
        String email = safeText(request.contactEmail()).toLowerCase();
        if (request.enabled()
                && request.mode() == TlsCertificateMode.AUTOMATIC
                && (!email.contains("@") || email.length() > 254)) {
            throw badRequest("A valid certificate contact email is required.");
        }
        if (request.mode() == TlsCertificateMode.MANUAL) {
            boolean certificate = StringUtils.hasText(request.certificate());
            boolean privateKey = StringUtils.hasText(request.privateKey());
            if (certificate != privateKey) {
                throw badRequest("Provide both the certificate chain and private key.");
            }
            TlsConfigurationDto installed = configuration();
            boolean reusableManualCertificate = installed.certificateConfigured()
                    && installed.mode() == TlsCertificateMode.MANUAL
                    && domain.equals(safeText(installed.domain()).toLowerCase());
            if (request.enabled()
                    && ("TEST_CERTIFICATE".equals(action) || "APPLY".equals(action))
                    && !certificate
                    && !reusableManualCertificate) {
                throw badRequest("A certificate chain and private key are required.");
            }
        }
        if (request.mode() != TlsCertificateMode.AUTOMATIC
                && request.autoRenew()) {
            throw badRequest("Automatic renewal requires automatic certificates.");
        }
    }

    private boolean available() {
        return properties.isEnabled()
                && MongoSessionModeContext.currentMode() == MongoSessionMode.REAL;
    }

    private void requireAvailable() {
        if (!available()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Managed HTTPS is unavailable in this deployment.");
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
