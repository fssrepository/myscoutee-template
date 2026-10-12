package com.raxim.myscoutee.domain.accounts.api;

import java.util.List;

/** Read-only local profile view; callers must not retain it as a persistence handle. */
public interface WorkspaceProfile extends DiscoveryProfile, EmailProfile, AssetProfile, ScoringProfile {
    com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess getIntegrationAccess();
    String getDeletedAtIso();
    boolean isOperator();
    String getId();
    Integer getCompletion();
    String getPartitionKey();
    ActivityCounts getActivities();
    List<? extends Device> getDevices();
    interface ActivityCounts extends EmailProfile.ActivityCounts { FeedbackCounts eventFeedback(); ChatCounts chat(); Integer adminJobs(); Integer events(); Integer hosting(); Integer game(); }
    interface ChatCounts { Integer all(); SupportCounts supportCases(); }
    interface SupportCounts { Integer pending(); Integer warned(); Integer picked(); }
    interface Device extends EmailProfile.Device {
        String getLastSeenAtIso();
        String getLastRegisteredAtIso();
        Boolean getNotificationsEnabled();
        String getFirebaseToken();
        @Override default boolean hasPushToken() {
            String token = getFirebaseToken();
            return token != null && org.springframework.util.StringUtils.hasText(token.trim());
        }
    }
    String getUserId();
    String getAccountUserId();
    String getWorkspaceGroupId();
    String getProfileStatus();
    String getStatus();
    String getHostTier();
    String getStatusText();
    String getEmail();
    String getInitials();
    String getGender();
    String getName();
    String getCity();
    List<String> getImages();
    List<String> getLanguages();
    Coordinates getLocationCoordinates();
    String getSyncKey();
    Integer getAge();
    String getHeight();
    String getPhysique();
    String getHoroscope();
    String getTraitLabel();
    String getHeadline();
    String getAbout();
    Double getAffinity();
    List<? extends ProfileDetailGroupRecord> getProfileDetails();
    UserImpressionsRecord getImpressions();
    java.util.Map<String, Double> getPaymentSpentTotals();
    java.util.Map<String, Double> getPaymentIncomeTotals();
    java.util.Map<String, Double> getPaymentAllTotals();
    Integer getPaymentRefundsPending();
    interface FeedbackCounts { Integer ownEvents(); Integer pending(); Integer feedbacked(); Integer removed(); }
}
