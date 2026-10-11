package com.raxim.myscoutee.domain.accounts.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "userLoginAudits")
@CompoundIndexes({
        @CompoundIndex(
                name = "user_login_audit_user_attempted",
                def = "{'userId': 1, 'attemptedAtIso': -1, '_id': -1}")
})
public class UserLoginAudit {
    public static final String OUTCOME_ACCEPTED = "ACCEPTED";
    public static final String OUTCOME_BLOCKED_LIMIT = "BLOCKED_LIMIT";

    @Id
    private String id;

    @Indexed(name = "user_login_audit_attempt", unique = true)
    private String attemptId;

    private String userId;
    private String sessionId;
    private String clientLabel;
    private String browser;
    private String operatingSystem;
    private String deviceType;
    private String provider;
    private String outcome;
    private String sourceAddress;
    private String userAgentHash;
    private String attemptedAtIso;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getClientLabel() { return clientLabel; }
    public void setClientLabel(String clientLabel) { this.clientLabel = clientLabel; }
    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }
    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getSourceAddress() { return sourceAddress; }
    public void setSourceAddress(String sourceAddress) { this.sourceAddress = sourceAddress; }
    public String getUserAgentHash() { return userAgentHash; }
    public void setUserAgentHash(String userAgentHash) { this.userAgentHash = userAgentHash; }
    public String getAttemptedAtIso() { return attemptedAtIso; }
    public void setAttemptedAtIso(String attemptedAtIso) { this.attemptedAtIso = attemptedAtIso; }
}
