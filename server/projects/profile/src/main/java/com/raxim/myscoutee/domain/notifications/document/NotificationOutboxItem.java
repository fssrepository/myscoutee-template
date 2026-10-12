package com.raxim.myscoutee.domain.notifications.document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notificationOutbox")
@CompoundIndexes({
        @CompoundIndex(name = "notification_outbox_group_status_retry", def = "{'baseGroupId': 1, 'status': 1, 'nextRetryAt': 1, 'createdDate': 1}"),
        @CompoundIndex(name = "notification_outbox_status_retry", def = "{'status': 1, 'nextRetryAt': 1, 'createdDate': 1}"),
        @CompoundIndex(name = "notification_outbox_collapse_status", def = "{'collapseKey': 1, 'status': 1}"),
        @CompoundIndex(name = "notification_outbox_entity_created", def = "{'entityType': 1, 'entityId': 1, 'createdDate': -1}")
})
public class NotificationOutboxItem implements com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.Receipt {
    private String baseGroupId;
    public String getBaseGroupId() { return baseGroupId; }
    public void setBaseGroupId(String baseGroupId) { this.baseGroupId = baseGroupId; }

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_FAILED = "failed";

    @Id
    private String id;

    @Indexed(name = "notification_outbox_sync_key", unique = true, sparse = true)
    private String syncKey;

    private String notificationType;
    private String entityType;
    private String entityId;
    private String recipientScope;
    private List<String> recipientUserIds = new ArrayList<>();
    // Null identifies records queued before delivery decisions were snapshotted.
    private Boolean firebaseDeliveryRequested;
    // User -> device -> token fingerprint. Provider tokens stay in users.devices.
    private Map<String, Map<String, String>> firebaseDevices;
    private String deliveryStrategy;
    private String topicName;
    private String title;
    private String body;
    private String clickUrl;
    private String iconUrl;
    private String badgeUrl;
    private String tag;
    private Map<String, String> payload = new LinkedHashMap<>();
    private String payloadHash;
    private String collapseKey;
    private String status;
    private Boolean pendingCounterTracked;
    private Integer retryCount;
    private String nextRetryAt;
    private String lockedUntil;
    private String lastError;
    private Integer tokenSuccessCount;
    private Integer tokenFailureCount;
    private String startedDate;
    private String sentDate;
    private String createdDate;
    private String createdUser;
    private String updatedDate;
    private String updatedUser;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSyncKey() {
        return syncKey;
    }

    public void setSyncKey(String syncKey) {
        this.syncKey = syncKey;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getRecipientScope() {
        return recipientScope;
    }

    public void setRecipientScope(String recipientScope) {
        this.recipientScope = recipientScope;
    }

    public List<String> getRecipientUserIds() {
        return recipientUserIds;
    }

    public void setRecipientUserIds(List<String> recipientUserIds) {
        this.recipientUserIds = recipientUserIds == null ? new ArrayList<>() : new ArrayList<>(recipientUserIds);
    }

    public Boolean getFirebaseDeliveryRequested() {
        return firebaseDeliveryRequested;
    }

    public void setFirebaseDeliveryRequested(Boolean requested) {
        this.firebaseDeliveryRequested = requested;
    }

    public Map<String, Map<String, String>> getFirebaseDevices() {
        return firebaseDevices;
    }

    public void setFirebaseDevices(Map<String, Map<String, String>> devices) {
        this.firebaseDevices = devices == null ? null : new LinkedHashMap<>(devices);
    }

    public String getDeliveryStrategy() {
        return deliveryStrategy;
    }

    public void setDeliveryStrategy(String deliveryStrategy) {
        this.deliveryStrategy = deliveryStrategy;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getClickUrl() {
        return clickUrl;
    }

    public void setClickUrl(String clickUrl) {
        this.clickUrl = clickUrl;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getBadgeUrl() {
        return badgeUrl;
    }

    public void setBadgeUrl(String badgeUrl) {
        this.badgeUrl = badgeUrl;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, String> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getCollapseKey() {
        return collapseKey;
    }

    public void setCollapseKey(String collapseKey) {
        this.collapseKey = collapseKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getPendingCounterTracked() {
        return pendingCounterTracked;
    }

    public void setPendingCounterTracked(Boolean pendingCounterTracked) {
        this.pendingCounterTracked = pendingCounterTracked;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(String nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(String lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Integer getTokenSuccessCount() {
        return tokenSuccessCount;
    }

    public void setTokenSuccessCount(Integer tokenSuccessCount) {
        this.tokenSuccessCount = tokenSuccessCount;
    }

    public Integer getTokenFailureCount() {
        return tokenFailureCount;
    }

    public void setTokenFailureCount(Integer tokenFailureCount) {
        this.tokenFailureCount = tokenFailureCount;
    }

    public String getStartedDate() {
        return startedDate;
    }

    public void setStartedDate(String startedDate) {
        this.startedDate = startedDate;
    }

    public String getSentDate() {
        return sentDate;
    }

    public void setSentDate(String sentDate) {
        this.sentDate = sentDate;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedUser() {
        return createdUser;
    }

    public void setCreatedUser(String createdUser) {
        this.createdUser = createdUser;
    }

    public String getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(String updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getUpdatedUser() {
        return updatedUser;
    }

    public void setUpdatedUser(String updatedUser) {
        this.updatedUser = updatedUser;
    }
}
