package com.raxim.myscoutee.domain.content.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "userReports")
@CompoundIndexes({
        @CompoundIndex(name = "user_report_user_created", def = "{'userId': 1, 'createdDate': -1}")
})
public class UserReportSubmission {
    @Id
    private String id;

    @Indexed(name = "user_report_sync_key", unique = true, sparse = true)
    private String syncKey;

    private String userId;
    private String handle;
    private String reason;
    private String details;
    private String targetUserId;
    private String memberEntryId;
    private String eventId;
    private String eventTitle;
    private String eventStartAtIso;
    private String sourceType;
    private String sourceId;
    private String sourceText;
    private String chatId;
    private String messageId;
    private String assetId;
    private String assetType;
    private String createdDate;
    private String createdUser;
    private String warnedAtIso;
    private String warnedByAdminUserId;
    @org.springframework.data.mongodb.core.index.Indexed(name = "user_report_resolved")
    private String resolvedAtIso;
    private String resolvedByAdminUserId;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getMemberEntryId() {
        return memberEntryId;
    }

    public void setMemberEntryId(String memberEntryId) {
        this.memberEntryId = memberEntryId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public String getEventStartAtIso() {
        return eventStartAtIso;
    }

    public void setEventStartAtIso(String eventStartAtIso) {
        this.eventStartAtIso = eventStartAtIso;
    }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedUser() {
        return createdUser;
    }

    public void setCreatedUser(String createdUser) {
        this.createdUser = createdUser;
    }

    public String getWarnedAtIso() {
        return warnedAtIso;
    }

    public void setWarnedAtIso(String warnedAtIso) {
        this.warnedAtIso = warnedAtIso;
    }

    public String getWarnedByAdminUserId() {
        return warnedByAdminUserId;
    }

    public void setWarnedByAdminUserId(String warnedByAdminUserId) {
        this.warnedByAdminUserId = warnedByAdminUserId;
    }

    public String getResolvedAtIso() {
        return resolvedAtIso;
    }

    public void setResolvedAtIso(String resolvedAtIso) {
        this.resolvedAtIso = resolvedAtIso;
    }

    public String getResolvedByAdminUserId() {
        return resolvedByAdminUserId;
    }

    public void setResolvedByAdminUserId(String resolvedByAdminUserId) {
        this.resolvedByAdminUserId = resolvedByAdminUserId;
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
