package com.raxim.myscoutee.domain.accounts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Native account contracts retained by the minimal template. */
public final class AuthDtos {
    private AuthDtos() {}

    public record OperatorBootstrapLoginRequestDto(
            @NotBlank @Email @Size(max = 254) String email,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotBlank @Size(max = 512) String password) {
    }

    public record OperatorBootstrapLoginResponseDto(
            String tokenType,
            String accessToken,
            String email,
            String expiresAt) {
    }

    public record UserLogoutRequestDto(
            String userId) {
    }

    public record UserSessionLoginRequestDto(
            @NotBlank @Size(max = 128) String attemptId,
            @Size(max = 32) String provider) {
    }

    public record UserSessionLoginResponseDto(
            boolean accepted,
            boolean existingSession,
            String outcome,
            Integer activeSessionCount,
            Integer maxActiveSessions,
            String message) {
    }

    public record UserDeleteRequestDto(
            String userId) {
    }

    public record FacebookDataDeletionResponseDto(
            String url,
            String confirmation_code) {
    }

    public record UserSubmitActionResponseDto(
            boolean submitted,
            String message) {
    }
}
