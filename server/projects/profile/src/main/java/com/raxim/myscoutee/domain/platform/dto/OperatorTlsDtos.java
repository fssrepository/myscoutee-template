package com.raxim.myscoutee.domain.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class OperatorTlsDtos {
    private OperatorTlsDtos() {
    }

    public enum TlsCertificateMode {
        AUTOMATIC,
        MANUAL
    }

    public enum TlsTestKind {
        DOMAIN,
        CERTIFICATE
    }

    public enum TlsJobPhase {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        REJECTED
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TlsConfigurationDto(
            String capability,
            String unavailableReason,
            boolean enabled,
            TlsCertificateMode mode,
            String domain,
            String contactEmail,
            boolean autoRenew,
            boolean certificateConfigured,
            String certificateIssuer,
            String certificateExpiresAt,
            String updatedAt) {
    }

    public record TlsConfigurationUpdateDto(
            boolean enabled,
            @NotNull TlsCertificateMode mode,
            @Size(max = 253) String domain,
            @Email @Size(max = 254) String contactEmail,
            boolean autoRenew,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @Size(max = 131072) String certificate,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @Size(max = 131072) String privateKey) {
        @Override
        public String toString() {
            return "TlsConfigurationUpdateDto["
                    + "enabled=" + enabled
                    + ", mode=" + mode
                    + ", domain=" + domain
                    + ", contactEmailProvided=" + hasText(contactEmail)
                    + ", autoRenew=" + autoRenew
                    + ", certificateProvided=" + hasText(certificate)
                    + ", privateKeyProvided=" + hasText(privateKey)
                    + "]";
        }
    }

    public record TlsTestRequestDto(
            @NotNull TlsTestKind kind,
            @NotNull TlsConfigurationUpdateDto configuration) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TlsJobDto(
            String jobId,
            TlsJobPhase phase,
            int percent,
            String message,
            String updatedAt,
            TlsConfigurationDto configuration) {
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
