package com.raxim.myscoutee.domain.accounts.service;

import org.springframework.util.StringUtils;
import com.raxim.myscoutee.domain.accounts.document.Users;

/** Shared interpretation of the existing persisted account status fields. */
final class ProfileStatusValues {
    static final String PROFILE_STATUS_ONBOARDING = "onboarding";
    private ProfileStatusValues() {}

    static String resolveProfileStatus(Users user) {
        return resolveProfileStatus(user == null ? null : user.getStatus(), user == null ? null : user.getProfileStatus());
    }

    static String resolveProfileStatus(String status, String fallbackProfileStatus) {
        String normalizedProfileStatus = safeText(fallbackProfileStatus);
        if ("blocked".equals(normalizedProfileStatus)) {
            return "blocked";
        }
        if (PROFILE_STATUS_ONBOARDING.equals(normalizedProfileStatus)) {
            return PROFILE_STATUS_ONBOARDING;
        }
        return switch (safeText(status)) {
            case "D" -> "deleted";
            case "B" -> "blocked";
            case "I" -> "inactive";
            case "F" -> "friends only";
            case "H" -> "host only";
            case "A" -> "public";
            default -> {
                yield StringUtils.hasText(normalizedProfileStatus) ? normalizedProfileStatus : "public";
            }
        };
    }

    private static String safeText(String value) { return value == null ? "" : value.trim(); }
}
