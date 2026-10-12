package com.raxim.myscoutee.domain.accounts.api;

import java.util.List;

/** Stored account facts consumed by affinity calculations; no auth, devices, payments or integrations. */
public interface ScoringProfile extends AssetAffinityProfile {
    String getId();
    String getUserId();
    String getWorkspaceGroupId();
    String getSyncKey();
    String getCity();
    String getPhysique();
    List<String> getLanguages();
    String getHoroscope();
    String getGender();
    String getHostTier();
    String getTraitLabel();
    String getHeadline();
    String getAbout();
    Integer getAge();
    String getHeight();
    Integer getCompletion();
    Double getAffinity();
    Coordinates getLocationCoordinates();
    List<? extends ProfileDetailGroupRecord> getProfileDetails();
    UserImpressionsRecord getImpressions();
    interface Coordinates { Double latitude(); Double longitude(); }
    @com.fasterxml.jackson.annotation.JsonIgnore
    @org.springframework.data.annotation.Transient
    @Override default com.raxim.myscoutee.domain.accounts.dto.HostConfidenceFactsDto getHostConfidenceFacts() {
        var impressions = getImpressions();
        var host = impressions == null ? null : impressions.host();
        if (host == null) return null;
        return new com.raxim.myscoutee.domain.accounts.dto.HostConfidenceFactsDto(
                host.averageRating(), host.peopleMet(), host.totalEvents(), host.repeatCount(),
                host.personalityTraits() == null ? null : host.personalityTraits().stream()
                        .map(trait -> trait == null ? null : trait.evidenceCount()).toList());
    }
    interface ProfileDetailGroupRecord { String title(); List<? extends ProfileDetailRowRecord> rows(); }
    interface ProfileDetailRowRecord { String labelKey(); String value(); String privacy(); List<String> options(); }
    interface UserImpressionsRecord { UserImpressionsSectionRecord host(); UserImpressionsSectionRecord member(); }
    interface UserImpressionsSectionRecord { Integer unreadCount(); Double averageRating(); Integer peopleMet(); Integer totalEvents(); Integer repeatCount(); Integer noShowCount(); List<String> vibeBadges(); List<String> personalityBadges(); List<? extends UserPersonalityTraitRecord> personalityTraits(); List<String> categoryBadges(); }
    interface UserPersonalityTraitRecord { String id(); String label(); Integer percent(); Integer evidenceCount(); String lastRatedAtIso(); }
}
