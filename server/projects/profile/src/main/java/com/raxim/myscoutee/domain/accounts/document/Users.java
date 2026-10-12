package com.raxim.myscoutee.domain.accounts.document;

import com.raxim.myscoutee.domain.integrations.dto.IntegrationAccess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.raxim.myscoutee.domain.accounts.dto.FeedCountersRecord;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
@CompoundIndexes({
        @CompoundIndex(name = "users_following_organizers", def = "{'following.organizerIds': 1}"),
        @CompoundIndex(name = "users_following_expiry", def = "{'following.nextExpiryAtIso': 1}"),
        @CompoundIndex(name = "users_profile_status", def = "{'profileStatus': 1}"),
        @CompoundIndex(name = "users_profile_status_deleted", def = "{'profileStatus': 1, 'deletedAtIso': 1}"),
        @CompoundIndex(name = "users_host_tier", def = "{'hostTier': 1}"),
        @CompoundIndex(
                name = "users_name_user_id",
                def = "{'name': 1, 'userId': 1}",
                collation = "{ 'locale': 'en', 'strength': 2 }")
})
public class Users implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile {
    private IntegrationAccess integrationAccess;
    public IntegrationAccess getIntegrationAccess() { return integrationAccess; }
    public void setIntegrationAccess(IntegrationAccess value) { integrationAccess = value; }

    private String activeWorkspaceGroupId;
    public String getActiveWorkspaceGroupId() { return activeWorkspaceGroupId; }
    public void setActiveWorkspaceGroupId(String value) { activeWorkspaceGroupId = value; }
    @Indexed private String workspaceGroupId;
    @Indexed private String accountUserId;
    public String getWorkspaceGroupId() { return workspaceGroupId; }
    public void setWorkspaceGroupId(String value) { workspaceGroupId = value; }
    public String getAccountUserId() { return accountUserId; }
    public void setAccountUserId(String value) { accountUserId = value; }

    private FeedCountersRecord feedCounters;
    public FeedCountersRecord getFeedCounters() { return feedCounters; }
    public void setFeedCounters(FeedCountersRecord value) { feedCounters = value; }
    private FollowingRecord following;

    public FollowingRecord getFollowing() { return following; }
    public void setFollowing(FollowingRecord following) { this.following = following; }

    public record FollowingRecord(List<String> organizerIds, Integer eventCount, String nextExpiryAtIso) {}

    @Id
    private String mongoId;

    @Indexed(name = "users_sync_key", unique = true, sparse = true)
    private String syncKey;

    @Indexed(name = "users_user_id", unique = true)
    private String userId;

    @Indexed(name = "users_email", unique = true, sparse = true)
    private String email;
    private String deletionWarningFor;
    public String getDeletionWarningFor() { return deletionWarningFor; }
    public void setDeletionWarningFor(String value) { deletionWarningFor = value; }

    @Indexed(name = "users_firebase_uid", sparse = true)
    private String firebaseUid;

    @Indexed(name = "users_google_id", sparse = true)
    private String googleId;

    @Indexed(name = "users_facebook_id", sparse = true)
    private String facebookId;



    private String name;
    private Integer age;
    private String birthday;
    private String city;
    private String height;
    private String physique;
    private List<String> languages = new ArrayList<>();
    private String horoscope;
    private String initials;
    private String gender;
    private String statusText;
    private String hostTier;
    private Boolean operator;
    private String traitLabel;
    private Integer completion;
    private Integer profileFormVersion;
    private String headline;
    private String about;
    private Double affinity;
    private LocationCoordinatesRecord locationCoordinates;
    @Indexed(name = "users_partition_key", sparse = true)
    private String partitionKey;
    private List<String> images = new ArrayList<>();
    private List<ProfileDetailGroupRecord> profileDetails = new ArrayList<>();
    private UserImpressionsRecord impressions;
    private String status;
    private String profileStatus;
    private String previousProfileStatus;
    public record ProfileSupportWork(@org.springframework.data.mongodb.core.mapping.Field("id") String id, String adminUserId, String adminName, String adminInitials, String text) {}
    private List<ProfileSupportWork> profileSupportWork = new ArrayList<>();
    public List<ProfileSupportWork> getProfileSupportWork() { return profileSupportWork == null ? List.of() : profileSupportWork; }
    public void setProfileSupportWork(List<ProfileSupportWork> value) { profileSupportWork = new ArrayList<>(value); }
    private Map<String, Boolean> feedbackCounterReceipts = new LinkedHashMap<>();
    public Map<String, Boolean> getFeedbackCounterReceipts() { return feedbackCounterReceipts; }
    public void setFeedbackCounterReceipts(Map<String, Boolean> value) { feedbackCounterReceipts = value; }
    private String profileStatusWork;
    public String getProfileStatusWork() { return profileStatusWork; }
    public void setProfileStatusWork(String value) { profileStatusWork = value; }
    private String deletedAtIso;
    private Boolean accountPurgePendingCounterTracked;
    /*
     * Explicit accounting exclusion for synthetic, automated, test, or other
     * non-human accounts. A missing value means eligible; privileged and
     * inactive accounts are excluded independently by the QMAU calculator.
     */
    private Boolean networkMeasurementExcluded;
    private String networkMeasurementExclusionReason;
    private UserActivitiesRecord activities;
    private UserNotificationPreferencesRecord notificationPreferences;
    private List<UserDeviceRecord> devices = new ArrayList<>();
    private List<String> homeGameRatedUserIds;
    private List<String> homeGameRatedPairKeys;
    private Integer homeGameRateProjectionVersion;
    private String homeGameRateProjectionUpdatedAtIso;
    private Long homeGameLastRateActivityMs;
    private Map<String, Double> paymentSpentTotals = new LinkedHashMap<>();
    private Map<String, Double> paymentIncomeTotals = new LinkedHashMap<>();
    private Map<String, Double> paymentAllTotals = new LinkedHashMap<>();
    private List<String> cashReceiptReversals = new ArrayList<>();

    public List<String> getCashReceiptReversals() { return cashReceiptReversals; }
    public void setCashReceiptReversals(List<String> value) {
        cashReceiptReversals = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    private Integer paymentRefundsPending;
    private Integer contactRequestsPending;
    private Map<String, Long> contactPermissionCounterVersions = new LinkedHashMap<>();
    private Map<String, Long> contactPermissionContactVersions = new LinkedHashMap<>();

    public Map<String, Long> getContactPermissionCounterVersions() { return contactPermissionCounterVersions; }
    public void setContactPermissionCounterVersions(Map<String, Long> value) { contactPermissionCounterVersions = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public Map<String, Long> getContactPermissionContactVersions() { return contactPermissionContactVersions; }
    public void setContactPermissionContactVersions(Map<String, Long> value) { contactPermissionContactVersions = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }

    public String getMongoId() {
        return mongoId;
    }

    public void setMongoId(String mongoId) {
        this.mongoId = mongoId;
    }

    public String getSyncKey() {
        return syncKey;
    }

    public void setSyncKey(String syncKey) {
        this.syncKey = syncKey;
    }

    public String getId() {
        return userId;
    }

    public void setId(String id) {
        this.userId = id;
    }

    private String summaryCurrency;
    public String getSummaryCurrency() { return summaryCurrency; }
    public void setSummaryCurrency(String value) { summaryCurrency = value; }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setFirebaseUid(String firebaseUid) {
        this.firebaseUid = firebaseUid;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getFacebookId() {
        return facebookId;
    }

    public void setFacebookId(String facebookId) {
        this.facebookId = facebookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getPhysique() {
        return physique;
    }

    public void setPhysique(String physique) {
        this.physique = physique;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public String getHoroscope() {
        return horoscope;
    }

    public void setHoroscope(String horoscope) {
        this.horoscope = horoscope;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getHostTier() {
        return hostTier;
    }

    public void setHostTier(String hostTier) {
        this.hostTier = hostTier;
    }

    public Boolean getOperator() {
        return operator;
    }

    public boolean isOperator() {
        return Boolean.TRUE.equals(operator);
    }

    public void setOperator(Boolean operator) {
        this.operator = operator;
    }

    public Boolean getNetworkMeasurementExcluded() {
        return networkMeasurementExcluded;
    }

    public boolean isNetworkMeasurementExcluded() {
        return Boolean.TRUE.equals(networkMeasurementExcluded);
    }

    public void setNetworkMeasurementExcluded(Boolean value) {
        this.networkMeasurementExcluded = value;
    }

    public String getNetworkMeasurementExclusionReason() {
        return networkMeasurementExclusionReason;
    }

    public void setNetworkMeasurementExclusionReason(String value) {
        this.networkMeasurementExclusionReason = value;
    }

    public String getTraitLabel() {
        return traitLabel;
    }

    public void setTraitLabel(String traitLabel) {
        this.traitLabel = traitLabel;
    }

    public Integer getCompletion() {
        return completion;
    }

    public void setCompletion(Integer completion) {
        this.completion = completion;
    }

    public Integer getProfileFormVersion() {
        return profileFormVersion;
    }

    public void setProfileFormVersion(Integer profileFormVersion) {
        this.profileFormVersion = profileFormVersion;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    public Double getAffinity() {
        return affinity;
    }

    public void setAffinity(Double affinity) {
        this.affinity = affinity;
    }

    public LocationCoordinatesRecord getLocationCoordinates() {
        return locationCoordinates;
    }

    public void setLocationCoordinates(LocationCoordinatesRecord locationCoordinates) {
        this.locationCoordinates = locationCoordinates;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<ProfileDetailGroupRecord> getProfileDetails() {
        return profileDetails;
    }

    public void setProfileDetails(List<ProfileDetailGroupRecord> profileDetails) {
        this.profileDetails = profileDetails == null ? new ArrayList<>() : profileDetails;
    }

    public UserImpressionsRecord getImpressions() {
        return impressions;
    }

    public void setImpressions(UserImpressionsRecord impressions) {
        this.impressions = impressions;
    }

    public String getProfileStatus() {
        return profileStatus;
    }

    public void setProfileStatus(String profileStatus) {
        this.profileStatus = profileStatus;
    }

    public String getPreviousProfileStatus() {
        return previousProfileStatus;
    }

    public void setPreviousProfileStatus(String previousProfileStatus) {
        this.previousProfileStatus = previousProfileStatus;
    }

    public String getDeletedAtIso() {
        return deletedAtIso;
    }

    public void setDeletedAtIso(String deletedAtIso) {
        this.deletedAtIso = deletedAtIso;
    }

    public Boolean getAccountPurgePendingCounterTracked() {
        return accountPurgePendingCounterTracked;
    }

    public void setAccountPurgePendingCounterTracked(Boolean accountPurgePendingCounterTracked) {
        this.accountPurgePendingCounterTracked = accountPurgePendingCounterTracked;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UserActivitiesRecord getActivities() {
        return activities;
    }

    public void setActivities(UserActivitiesRecord activities) {
        this.activities = activities;
    }

    public UserNotificationPreferencesRecord getNotificationPreferences() {
        return notificationPreferences;
    }

    public void setNotificationPreferences(UserNotificationPreferencesRecord notificationPreferences) {
        this.notificationPreferences = notificationPreferences;
    }

    public List<UserDeviceRecord> getDevices() {
        return devices;
    }

    public void setDevices(List<UserDeviceRecord> devices) {
        this.devices = devices == null ? new ArrayList<>() : devices;
    }

    public List<String> getHomeGameRatedUserIds() {
        return homeGameRatedUserIds;
    }

    public void setHomeGameRatedUserIds(List<String> homeGameRatedUserIds) {
        this.homeGameRatedUserIds = homeGameRatedUserIds == null ? null : new ArrayList<>(homeGameRatedUserIds);
    }

    public List<String> getHomeGameRatedPairKeys() {
        return homeGameRatedPairKeys;
    }

    public void setHomeGameRatedPairKeys(List<String> homeGameRatedPairKeys) {
        this.homeGameRatedPairKeys = homeGameRatedPairKeys == null ? null : new ArrayList<>(homeGameRatedPairKeys);
    }

    public Integer getHomeGameRateProjectionVersion() {
        return homeGameRateProjectionVersion;
    }

    public void setHomeGameRateProjectionVersion(Integer homeGameRateProjectionVersion) {
        this.homeGameRateProjectionVersion = homeGameRateProjectionVersion;
    }

    public String getHomeGameRateProjectionUpdatedAtIso() {
        return homeGameRateProjectionUpdatedAtIso;
    }

    public void setHomeGameRateProjectionUpdatedAtIso(String homeGameRateProjectionUpdatedAtIso) {
        this.homeGameRateProjectionUpdatedAtIso = homeGameRateProjectionUpdatedAtIso;
    }

    public Long getHomeGameLastRateActivityMs() {
        return homeGameLastRateActivityMs;
    }

    public void setHomeGameLastRateActivityMs(Long homeGameLastRateActivityMs) {
        this.homeGameLastRateActivityMs = homeGameLastRateActivityMs;
    }

    public Map<String, Double> getPaymentSpentTotals() {
        return paymentSpentTotals;
    }

    public void setPaymentSpentTotals(Map<String, Double> paymentSpentTotals) {
        this.paymentSpentTotals = paymentSpentTotals == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(paymentSpentTotals);
    }

    public Map<String, Double> getPaymentIncomeTotals() {
        return paymentIncomeTotals;
    }

    public void setPaymentIncomeTotals(Map<String, Double> paymentIncomeTotals) {
        this.paymentIncomeTotals = paymentIncomeTotals == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(paymentIncomeTotals);
    }

    public Map<String, Double> getPaymentAllTotals() {
        return paymentAllTotals;
    }

    public void setPaymentAllTotals(Map<String, Double> paymentAllTotals) {
        this.paymentAllTotals = paymentAllTotals == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(paymentAllTotals);
    }

    public Integer getContactRequestsPending() { return contactRequestsPending; }
    public void setContactRequestsPending(Integer value) { contactRequestsPending = value; }

    public Integer getPaymentRefundsPending() {
        return paymentRefundsPending;
    }

    public void setPaymentRefundsPending(Integer paymentRefundsPending) {
        this.paymentRefundsPending = paymentRefundsPending;
    }

    public record LocationCoordinatesRecord(
            Double latitude,
            Double longitude) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.Coordinates {
    }

    public record UserActivitiesRecord(
            Integer game,
            Integer chats,
            Integer invitations,
            Integer events,
            Integer hosting,
            Integer cars,
            Integer accommodation,
            Integer supplies,
            Integer tickets,
            Integer contacts,
            Integer feedback,
            UserChatCountersRecord chat,
            UserEventCountersRecord event,
            UserAssetCountersRecord asset,
            UserEventFeedbackCountersRecord eventFeedback,
            Integer adminJobs,
            Integer adminMetrics,
            Integer notifications,
            Integer serviceFeedbackPending) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.ActivityCounts {
        public UserActivitiesRecord(
                Integer game,
                Integer chats,
                Integer invitations,
                Integer events,
                Integer hosting,
                Integer cars,
                Integer accommodation,
                Integer supplies,
                Integer tickets,
                Integer contacts,
                Integer feedback,
                UserChatCountersRecord chat,
                UserEventCountersRecord event,
                UserAssetCountersRecord asset,
                UserEventFeedbackCountersRecord eventFeedback,
                Integer adminJobs,
                Integer adminMetrics,
                Integer notifications) {
            this(game, chats, invitations, events, hosting, cars, accommodation, supplies, tickets,
                    contacts, feedback, chat, event, asset, eventFeedback, adminJobs, adminMetrics,
                    notifications, 0);
        }

        public UserActivitiesRecord(
                Integer game,
                Integer chats,
                Integer invitations,
                Integer events,
                Integer hosting,
                Integer cars,
                Integer accommodation,
                Integer supplies,
                Integer tickets,
                Integer contacts,
                Integer feedback,
                UserChatCountersRecord chat,
                UserEventCountersRecord event,
                UserAssetCountersRecord asset,
                UserEventFeedbackCountersRecord eventFeedback,
                Integer adminJobs,
                Integer adminMetrics) {
            this(
                    game,
                    chats,
                    invitations,
                    events,
                    hosting,
                    cars,
                    accommodation,
                    supplies,
                    tickets,
                    contacts,
                    feedback,
                    chat,
                    event,
                    asset,
                    eventFeedback,
                    adminJobs,
                    adminMetrics,
                    0);
        }
    }

    public record UserNotificationPreferencesRecord(Boolean muted) {
    }

    public record UserChatCountersRecord(
            Integer all,
            Integer event,
            Integer subEvent,
            Integer group,
            Integer service,
            Integer appSupport,
            UserSupportCaseCountersRecord supportCases,
            Integer groupSupport,
            Integer contacts,
            Integer campaign,
            Integer cases) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.ChatCounts {
        public UserChatCountersRecord(Integer all, Integer event, Integer subEvent, Integer group, Integer service,
                Integer appSupport, UserSupportCaseCountersRecord supportCases, Integer groupSupport, Integer contacts) {
            this(all, event, subEvent, group, service, appSupport, supportCases, groupSupport, contacts, 0, 0);
        }
        public UserChatCountersRecord(Integer all, Integer event, Integer subEvent, Integer group, Integer service,
                Integer appSupport, UserSupportCaseCountersRecord supportCases, Integer groupSupport) {
            this(all, event, subEvent, group, service, appSupport, supportCases, groupSupport, 0);
        }
        public UserChatCountersRecord(Integer all, Integer event, Integer subEvent, Integer group, Integer service,
                Integer appSupport, UserSupportCaseCountersRecord supportCases) {
            this(all, event, subEvent, group, service, appSupport, supportCases, 0);
        }
        public UserChatCountersRecord(
                Integer all,
                Integer event,
                Integer subEvent,
                Integer group,
                Integer service,
                Integer appSupport) {
            this(all, event, subEvent, group, service, appSupport,
                    new UserSupportCaseCountersRecord(0, 0, 0, 0, 0, 0));
        }
    }

    public record UserSupportCaseCountersRecord(
            Integer pending,
            Integer warned,
            Integer picked,
            Integer solved,
            Integer blocked,
            Integer all) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.SupportCounts {
    }

    public record UserEventCountersRecord(
            Integer all,
            Integer active,
            Integer pending,
            Integer invitations,
            Integer hosting,
            Integer drafts,
            Integer trash,
            Integer watchlist) {
        public UserEventCountersRecord(
                Integer all,
                Integer active,
                Integer pending,
                Integer invitations,
                Integer hosting,
                Integer drafts,
                Integer trash) {
            this(all, active, pending, invitations, hosting, drafts, trash, 0);
        }
    }

    public record UserAssetCountersRecord(
            Integer cars,
            Integer accommodation,
            Integer supplies,
            Integer tickets,
            Integer carsPending,
            Integer accommodationPending,
            Integer suppliesPending) {
        public UserAssetCountersRecord(
                Integer cars,
                Integer accommodation,
                Integer supplies,
                Integer tickets) {
            this(cars, accommodation, supplies, tickets, 0, 0, 0);
        }
    }

    public record UserEventFeedbackCountersRecord(
            Integer ownEvents,
            Integer pending,
            Integer feedbacked,
            Integer removed) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.FeedbackCounts {
    }

    public record ProfileDetailGroupRecord(
            String title,
            List<ProfileDetailRowRecord> rows) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.ProfileDetailGroupRecord {
    }

    public record ProfileDetailRowRecord(
            String labelKey,
            String value,
            String privacy,
            List<String> options) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.ProfileDetailRowRecord {
    }

    public static class UserDeviceRecord implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.Device {
        private String deviceId;
        private String platform;
        private String firebaseToken;
        private Boolean notificationsEnabled;
        private String lastRegisteredAtIso;
        private String lastSeenAtIso;

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getFirebaseToken() {
            return firebaseToken;
        }

        public void setFirebaseToken(String firebaseToken) {
            this.firebaseToken = firebaseToken;
        }

        public Boolean getNotificationsEnabled() {
            return notificationsEnabled;
        }

        public void setNotificationsEnabled(Boolean notificationsEnabled) {
            this.notificationsEnabled = notificationsEnabled;
        }

        public String getLastRegisteredAtIso() {
            return lastRegisteredAtIso;
        }

        public void setLastRegisteredAtIso(String lastRegisteredAtIso) {
            this.lastRegisteredAtIso = lastRegisteredAtIso;
        }

        public String getLastSeenAtIso() {
            return lastSeenAtIso;
        }

        public void setLastSeenAtIso(String lastSeenAtIso) {
            this.lastSeenAtIso = lastSeenAtIso;
        }
    }

    public record UserImpressionsRecord(
            UserImpressionsSectionRecord host,
            UserImpressionsSectionRecord member) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.UserImpressionsRecord {
    }

    public record UserImpressionsSectionRecord(
            Integer unreadCount,
            Double averageRating,
            Integer peopleMet,
            Integer totalEvents,
            Integer repeatCount,
            Integer noShowCount,
            List<String> vibeBadges,
            List<String> personalityBadges,
            List<UserPersonalityTraitRecord> personalityTraits,
            List<String> categoryBadges) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.UserImpressionsSectionRecord {
    }

    public record UserPersonalityTraitRecord(
            String id,
            String label,
            Integer percent,
            Integer evidenceCount,
            String lastRatedAtIso) implements com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile.UserPersonalityTraitRecord {
    }
}
