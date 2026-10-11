package com.raxim.myscoutee.domain.accounts.dto;

import java.util.List;
import com.raxim.myscoutee.common.dto.SharedDtos.LocationCoordinatesDto;

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
    public record UserMenuCountersDto(Integer chats, UserChatCountersDto chat, Integer notifications) {}

    public record UserChatCountersDto(Integer all, Integer appSupport,
            UserSupportCaseCountersDto supportCases, Integer contacts) {}

    public record UserSupportCaseCountersDto(
            Integer pending,
            Integer warned,
            Integer picked,
            Integer solved,
            Integer blocked,
            Integer all) {
    }
    public record UserDto(String id, String name, String city, String initials, String gender,
            LocationCoordinatesDto locationCoordinates, String partitionKey, List<String> images,
            String profileStatus, String previousProfileStatus, String deletedAtIso,
            Boolean admin, Boolean operator, UserMenuCountersDto activities,
            UserNotificationPreferencesDto notificationPreferences) {}

    public record UserByIdResponseDto(String id, String name, String city, String initials, String gender,
            LocationCoordinatesDto locationCoordinates, String partitionKey, List<String> images,
            String profileStatus, String previousProfileStatus, String deletedAtIso,
            Boolean admin, Boolean operator, UserMenuCountersDto activities,
            UserNotificationPreferencesDto notificationPreferences,
            UserMenuCountersDto counterOverrides) {}

    public record ProfileExtDto(UserDto profile) {}
    public record ProfileExtByIdResponseDto(ProfileExtDto profileExt, UserMenuCountersDto counterOverrides) {}
    public record DemoUserListItemDto(String id, LocationCoordinatesDto locationCoordinates,
            boolean locationRequired, String name, String city, String initials, String avatarUrl,
            String gender, String statusText, Integer completion, Integer profileFormVersion,
            String status, String profileStatus, String deletedAtIso) {}
    public record UserRealtimeLongPollRequestDto(String userId, String cursor) {}
    public record UserRealtimeLongPollResponseDto(String userId, String profileStatus,
            LocationCoordinatesDto locationCoordinates, UserMenuCountersDto counters,
            List<UserNotificationDeviceDto> notificationDevices, String cursor, String serverTsIso) {}

    public record UserLocationEligibilityResponseDto(
            boolean eligible,
            String partitionKey,
            String message,
            boolean securityGateEnabled,
            boolean locationRequired) {
    }
    public record UserNotificationPreferencesDto(
            Boolean muted) {
    }
    public record UserNotificationDeviceDto(String deviceId, boolean notificationsEnabled) {
    }
}
