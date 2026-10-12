package com.raxim.myscoutee.domain.platform.mapper;

import org.springframework.stereotype.Component;

import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsCertificateMode;
import com.raxim.myscoutee.domain.platform.dto.OperatorTlsDtos.TlsConfigurationDto;

@Component
public class OperatorTlsMapper {
    public TlsConfigurationDto available(TlsConfigurationDto value) {
        if (value == null) {
            return new TlsConfigurationDto(
                    "AVAILABLE",
                    null,
                    false,
                    TlsCertificateMode.AUTOMATIC,
                    "",
                    "",
                    true,
                    false,
                    null,
                    null,
                    null);
        }
        return new TlsConfigurationDto(
                "AVAILABLE",
                null,
                value.enabled(),
                value.mode() == null ? TlsCertificateMode.AUTOMATIC : value.mode(),
                safeText(value.domain()).toLowerCase(),
                safeText(value.contactEmail()).toLowerCase(),
                value.autoRenew(),
                value.certificateConfigured(),
                nullableText(value.certificateIssuer()),
                nullableText(value.certificateExpiresAt()),
                nullableText(value.updatedAt()));
    }

    public TlsConfigurationDto unavailable() {
        return new TlsConfigurationDto(
                "BACKEND_UNAVAILABLE",
                "operator.configuration.tls.backend.unavailable",
                false,
                TlsCertificateMode.AUTOMATIC,
                "",
                "",
                false,
                false,
                null,
                null,
                null);
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullableText(String value) {
        String normalized = safeText(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
