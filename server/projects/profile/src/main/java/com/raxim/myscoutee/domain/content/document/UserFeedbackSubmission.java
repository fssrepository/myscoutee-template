package com.raxim.myscoutee.domain.content.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "userFeedback")
@CompoundIndexes({
        @CompoundIndex(name = "user_feedback_user_created", def = "{'userId': 1, 'createdDate': -1}")
})
public class UserFeedbackSubmission {
    @Id
    private String id;

    @Indexed(name = "user_feedback_sync_key", unique = true, sparse = true)
    private String syncKey;

    private String userId;
    private String category;
    private String subject;
    private String details;
    private String createdDate;
    private String createdUser;
    @org.springframework.data.mongodb.core.index.Indexed(name = "user_feedback_resolved")
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
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

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedUser() {
        return createdUser;
    }

    public void setCreatedUser(String createdUser) {
        this.createdUser = createdUser;
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
