package com.raxim.myscoutee.domain.notifications.document;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "userNotifications")
@CompoundIndexes({
        @CompoundIndex(
                name = "user_notifications_recipient_read_created",
                def = "{'recipientUserId': 1, 'readAtIso': 1, 'createdDate': -1, '_id': -1}"),
        @CompoundIndex(
                name = "user_notifications_recipient_created",
                def = "{'recipientUserId': 1, 'createdDate': -1, '_id': -1}"),
        @CompoundIndex(
                name = "user_notifications_source_created",
                def = "{'sourceType': 1, 'sourceId': 1, 'createdDate': -1}"),
        @CompoundIndex(
                name = "user_notifications_recipient_kind_source",
                def = "{'recipientUserId': 1, 'kind': 1, 'sourceType': 1, 'sourceId': 1, 'readAtIso': 1}")
})
public class UserNotification {
    @Id
    private String id;

    @Indexed(name = "user_notifications_sync_key", unique = true, sparse = true)
    private String syncKey;

    @Indexed(name = "user_notifications_aggregation_key", unique = true, sparse = true)
    private String aggregationKey;

    private String outboxId;
    private String recipientUserId;
    private String kind;
    private String category;
    private String title;
    private String message;
    private String senderUserId;
    private String actionPath;
    private String sourceType;
    private String sourceId;
    private Map<String, String> payload = new LinkedHashMap<>();
    private Integer occurrenceCount;
    private Long revision;
    private String readAtIso;
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

    public String getAggregationKey() {
        return aggregationKey;
    }

    public void setAggregationKey(String aggregationKey) {
        this.aggregationKey = aggregationKey;
    }

    public String getOutboxId() {
        return outboxId;
    }

    public void setOutboxId(String outboxId) {
        this.outboxId = outboxId;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(String recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(String senderUserId) {
        this.senderUserId = senderUserId;
    }

    public String getActionPath() {
        return actionPath;
    }

    public void setActionPath(String actionPath) {
        this.actionPath = actionPath;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, String> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    public Integer getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(Integer occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public Long getRevision() {
        return revision;
    }

    public void setRevision(Long revision) {
        this.revision = revision;
    }

    public String getReadAtIso() {
        return readAtIso;
    }

    public void setReadAtIso(String readAtIso) {
        this.readAtIso = readAtIso;
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
