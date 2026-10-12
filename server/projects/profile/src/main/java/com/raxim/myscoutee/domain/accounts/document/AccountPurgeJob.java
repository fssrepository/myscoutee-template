package com.raxim.myscoutee.domain.accounts.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "accountPurgeJobs")
@CompoundIndexes({
        @CompoundIndex(name = "account_purge_status_retry", def = "{'status': 1, 'nextRetryAtIso': 1, 'lockedUntilIso': 1}"),
        @CompoundIndex(name = "account_purge_hash", def = "{'userHash': 1}")
})
public class AccountPurgeJob {
    private String baseGroupId;
    public String getBaseGroupId() { return baseGroupId; }
    public void setBaseGroupId(String baseGroupId) { this.baseGroupId = baseGroupId; }

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    @Id
    private String id;

    @Indexed(name = "account_purge_user_id", unique = true, sparse = true)
    private String userId;

    private String userHash;
    private String status;
    private String stage;
    private List<String> ownedEventIds = new ArrayList<>();
    private List<String> ownedAssetIds = new ArrayList<>();
    private Integer retryCount;
    private String nextRetryAtIso;
    private String lockedUntilIso;
    private String startedAtIso;
    private String completedAtIso;
    private String lastError;
    private String createdDate;
    private String updatedDate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserHash() { return userHash; }
    public void setUserHash(String userHash) { this.userHash = userHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public List<String> getOwnedEventIds() { return ownedEventIds; }
    public void setOwnedEventIds(List<String> ownedEventIds) {
        this.ownedEventIds = ownedEventIds == null ? new ArrayList<>() : new ArrayList<>(ownedEventIds);
    }
    public List<String> getOwnedAssetIds() { return ownedAssetIds; }
    public void setOwnedAssetIds(List<String> ownedAssetIds) {
        this.ownedAssetIds = ownedAssetIds == null ? new ArrayList<>() : new ArrayList<>(ownedAssetIds);
    }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getNextRetryAtIso() { return nextRetryAtIso; }
    public void setNextRetryAtIso(String nextRetryAtIso) { this.nextRetryAtIso = nextRetryAtIso; }
    public String getLockedUntilIso() { return lockedUntilIso; }
    public void setLockedUntilIso(String lockedUntilIso) { this.lockedUntilIso = lockedUntilIso; }
    public String getStartedAtIso() { return startedAtIso; }
    public void setStartedAtIso(String startedAtIso) { this.startedAtIso = startedAtIso; }
    public String getCompletedAtIso() { return completedAtIso; }
    public void setCompletedAtIso(String completedAtIso) { this.completedAtIso = completedAtIso; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
}
