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
        java.util.List<String> images) implements ProfileSummary {
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
    @Override public String getUserId() { return userId; }
    @Override public String getAccountUserId() { return accountUserId; }
    @Override public String getWorkspaceGroupId() { return workspaceGroupId; }
    @Override public String getDeletedAtIso() { return deletedAtIso; }
    @Override public String getProfileStatus() { return profileStatus; }
    @Override public String getStatus() { return status; }
    @Override public String getHostTier() { return hostTier; }
    @Override public String getStatusText() { return statusText; }
    @Override public Integer getAge() { return age; }
    @Override public String getCity() { return city; }
    @Override public String getAbout() { return about; }
    @Override public String getHeadline() { return headline; }
    @Override public String getTraitLabel() { return traitLabel; }
    @Override public String getName() { return name; }
    @Override public String getInitials() { return initials; }
    @Override public String getGender() { return gender; }
    @Override public java.util.List<String> getImages() { return images; }
}
