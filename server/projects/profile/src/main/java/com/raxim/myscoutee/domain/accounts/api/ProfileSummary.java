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

    /** Record-shaped implementations share their existing bean accessor bridge here. */
    interface RecordFields extends ProfileSummary {
        String userId();
        @Override default String getUserId() { return userId(); }
        String accountUserId();
        @Override default String getAccountUserId() { return accountUserId(); }
        String workspaceGroupId();
        @Override default String getWorkspaceGroupId() { return workspaceGroupId(); }
        String deletedAtIso();
        @Override default String getDeletedAtIso() { return deletedAtIso(); }
        String profileStatus();
        @Override default String getProfileStatus() { return profileStatus(); }
        String status();
        @Override default String getStatus() { return status(); }
        String hostTier();
        @Override default String getHostTier() { return hostTier(); }
        String statusText();
        @Override default String getStatusText() { return statusText(); }
        String name();
        @Override default String getName() { return name(); }
        String headline();
        @Override default String getHeadline() { return headline(); }
        String about();
        @Override default String getAbout() { return about(); }
        String traitLabel();
        @Override default String getTraitLabel() { return traitLabel(); }
        Integer age();
        @Override default Integer getAge() { return age(); }
        String city();
        @Override default String getCity() { return city(); }
        String initials();
        @Override default String getInitials() { return initials(); }
        String gender();
        @Override default String getGender() { return gender(); }
        java.util.List<String> images();
        @Override default java.util.List<String> getImages() { return images(); }
    }


}
