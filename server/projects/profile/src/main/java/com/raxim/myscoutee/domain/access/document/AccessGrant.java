package com.raxim.myscoutee.domain.access.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "accessGrants")
@CompoundIndexes({
        @CompoundIndex(name = "access_grant_subject_permission_resource", def = "{'subjectUserId': 1, 'permission': 1, 'resourceType': 1, 'resourceId': 1}"),
        @CompoundIndex(name = "access_grant_resource_permission", def = "{'resourceType': 1, 'resourceId': 1, 'permission': 1}")
})
public class AccessGrant {
    @Id
    private String id;

    @Indexed(name = "access_grant_sync_key", unique = true, sparse = true)
    private String syncKey;

    private String subjectUserId;
    private String resourceType;
    private String resourceId;
    private String permission;
    private String effect;
    private String expiresAtIso;
    private String createdByUserId;
    private String createdDate;
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

    public String getSubjectUserId() {
        return subjectUserId;
    }

    public void setSubjectUserId(String subjectUserId) {
        this.subjectUserId = subjectUserId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public String getExpiresAtIso() {
        return expiresAtIso;
    }

    public void setExpiresAtIso(String expiresAtIso) {
        this.expiresAtIso = expiresAtIso;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
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
