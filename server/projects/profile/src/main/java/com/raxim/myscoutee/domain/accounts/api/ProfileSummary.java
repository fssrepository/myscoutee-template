package com.raxim.myscoutee.domain.accounts.api;

/** Identity, lifecycle and display facts; excludes private devices, finances and integration configuration. */
public interface ProfileSummary extends WorkspaceIdentity {
    String getUserId();
    String getAccountUserId();
    String getWorkspaceGroupId();
    String getDeletedAtIso();
    String getProfileStatus();
    String getStatus();
    String getHostTier();
    String getStatusText();
    String getName();
    String getHeadline();
    String getAbout();
    String getTraitLabel();
    Integer getAge();
    String getCity();
    String getInitials();
    String getGender();
    java.util.List<String> getImages();
}
