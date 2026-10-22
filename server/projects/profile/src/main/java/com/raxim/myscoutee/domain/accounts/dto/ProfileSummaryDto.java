package com.raxim.myscoutee.domain.accounts.dto;

import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;

/** Purpose-limited account summary for presentation and workspace/lifecycle checks. */
public record ProfileSummaryDto(
        String userId,
        String accountUserId,
        String workspaceGroupId,
        String deletedAtIso,
        String profileStatus,
        String status,
        String hostTier,
        String statusText,
        String name,
        String headline, String about,
        String traitLabel,
        Integer age,
        String city,
        String initials,
        String gender,
        java.util.List<String> images) implements ProfileSummary, com.raxim.myscoutee.domain.accounts.api.ProfileSummary.RecordFields {
    public ProfileSummaryDto {
        if (images != null) images = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(images));
    }
    public static ProfileSummaryDto from(ProfileSummary value) {
        return new ProfileSummaryDto(
                value.getUserId(),
                value.getAccountUserId(),
                value.getWorkspaceGroupId(),
                value.getDeletedAtIso(),
                value.getProfileStatus(),
                value.getStatus(),
                value.getHostTier(),
                value.getStatusText(),
                value.getName(),
                value.getHeadline(), value.getAbout(),
                value.getTraitLabel(),
                value.getAge(),
                value.getCity(),
                value.getInitials(),
                value.getGender(),
                value.getImages());
    }
}
