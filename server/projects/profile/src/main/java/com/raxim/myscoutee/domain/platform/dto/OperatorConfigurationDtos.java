package com.raxim.myscoutee.domain.platform.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class OperatorConfigurationDtos {
    private OperatorConfigurationDtos() {
    }

    public enum ConfigurationTestKind {
        FIREBASE_AUTHENTICATION,
        FIREBASE_MESSAGING
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeploymentBrandingDto(
            String productName,
            Integer logoCharacterIndex,
            String homeLabel,
            String logoUrl,
            long revision,
            List<DeploymentSocialLinkDto> socialLinks) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeploymentPrivacyContactDto(
            boolean configured,
            String dataControllerName,
            String privacyContactEmail) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeploymentPublicConfigurationDto(
            String productName,
            Integer logoCharacterIndex,
            String homeLabel,
            String logoUrl,
            long revision,
            boolean firebaseMessagingConfigured,
            List<DeploymentSocialLinkDto> socialLinks,
            DeploymentPrivacyContactDto privacyContact) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeploymentSocialLinkDto(
            @NotBlank @Size(max = 32) String provider,
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Size(max = 2048) String url,
            @Size(max = 64) String icon,
            @Size(max = 80) String handle) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IntegrationConfigurationDto(
            String publicBaseUrl) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FirebasePublicConfigurationDto(
            long revision,
            String apiKey,
            String authDomain,
            String projectId,
            String storageBucket,
            String messagingSenderId,
            String appId,
            String measurementId,
            String vapidKey) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FirebaseConfigurationDto(
            String projectId,
            boolean authenticationCredentialConfigured,
            boolean messagingCredentialConfigured,
            FirebasePublicConfigurationDto publicConfiguration,
            boolean active,
            boolean readyToActivate,
            String authenticationTestedAt,
            String messagingTestedAt,
            String activatedAt) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OperatorConfigurationDto(
            String capability,
            String unavailableReason,
            List<String> adminEmails,
            DeploymentPrivacyContactDto privacyContact,
            List<DeploymentSocialLinkDto> socialLinks,
            DeploymentBrandingDto branding,
            IntegrationConfigurationDto integration,
            FirebaseConfigurationDto firebase,
            String updatedAt) {
    }

    public record BrandingUpdateDto(
            @NotBlank @Size(max = 80) String productName,
            Integer logoCharacterIndex,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @Size(max = 120) String homeLabel,
            @NotNull @Size(max = 2048) String logoUrl) {
        public BrandingUpdateDto(
                String productName,
                Integer logoCharacterIndex,
                String logoUrl) {
            this(
                    productName,
                    logoCharacterIndex,
                    null,
                    logoUrl);
        }
    }

    public record IntegrationUpdateDto(
            @Size(max = 2048) String publicBaseUrl) {
    }

    public record PrivacyContactUpdateDto(
            @Size(max = 160) String dataControllerName,
            @Size(max = 254) String privacyContactEmail) {
    }

    public record FirebaseUpdateDto(
            @Size(max = 160) String projectId,
            @Size(max = 256) String apiKey,
            @Size(max = 253) String authDomain,
            @Size(max = 512) String storageBucket,
            @Size(max = 64) String messagingSenderId,
            @Size(max = 256) String appId,
            @Size(max = 64) String measurementId,
            @Size(max = 512) String vapidKey,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @Size(max = 65536) String authenticationCredential,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @Size(max = 65536) String messagingCredential) {
        public FirebaseUpdateDto(
                String projectId,
                String authenticationCredential,
                String messagingCredential) {
            this(
                    projectId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    authenticationCredential,
                    messagingCredential);
        }

        /**
         * Record defaults include every component and are unsafe for
         * credentials if a request is ever included in diagnostic output.
         */
        @Override
        public String toString() {
            return "FirebaseUpdateDto["
                    + "projectIdProvided="
                    + hasText(projectId)
                    + ", publicConfigurationProvided="
                    + (hasText(apiKey)
                            || hasText(authDomain)
                            || hasText(storageBucket)
                            || hasText(messagingSenderId)
                            || hasText(appId)
                            || hasText(measurementId)
                            || hasText(vapidKey))
                    + ", authenticationCredentialProvided="
                    + hasText(authenticationCredential)
                    + ", messagingCredentialProvided="
                    + hasText(messagingCredential)
                    + "]";
        }
    }

    public record OperatorConfigurationUpdateDto(
            @NotNull @Size(max = 32)
            List<@NotBlank @Email @Size(max = 254) String> adminEmails,
            @Valid PrivacyContactUpdateDto privacyContact,
            @NotNull @Size(max = 12)
            List<@NotNull @Valid DeploymentSocialLinkDto> socialLinks,
            @NotNull @Valid BrandingUpdateDto branding,
            @Valid IntegrationUpdateDto integration,
            @Valid FirebaseUpdateDto firebase) {
    }

    public record ConfigurationTestRequestDto(
            @NotNull ConfigurationTestKind kind,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @Size(max = 4096) String destinationToken,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @Size(max = 4096) String browserReadinessToken,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            Long browserConfigurationRevision,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @Size(max = 256) String browserAppId) {
        public ConfigurationTestRequestDto(ConfigurationTestKind kind) {
            this(kind, null, null, null, null);
        }

        public ConfigurationTestRequestDto(
                ConfigurationTestKind kind,
                String destinationToken) {
            this(kind, destinationToken, null, null, null);
        }

        /**
         * FCM registration tokens must not appear in request diagnostics.
         */
        @Override
        public String toString() {
            return "ConfigurationTestRequestDto["
                    + "kind="
                    + kind
                    + ", destinationTokenProvided="
                    + hasText(destinationToken)
                    + ", browserReadinessTokenProvided="
                    + hasText(browserReadinessToken)
                    + ", browserConfigurationRevision="
                    + browserConfigurationRevision
                    + ", browserAppIdProvided="
                    + hasText(browserAppId)
                    + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ConfigurationTestResultDto(
            ConfigurationTestKind kind,
            boolean success,
            String message,
            String testedAt,
            FirebaseConfigurationDto firebase) {
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
