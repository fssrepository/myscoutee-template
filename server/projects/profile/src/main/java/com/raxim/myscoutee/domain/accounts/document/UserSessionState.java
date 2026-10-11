package com.raxim.myscoutee.domain.accounts.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "userSessionStates")
public class UserSessionState {
    @MongoId(FieldType.STRING)
    private String id;

    private String userId;
    private List<ActiveSession> activeSessions = new ArrayList<>();
    private String createdDate;
    private String updatedDate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<ActiveSession> getActiveSessions() {
        return activeSessions;
    }

    public void setActiveSessions(List<ActiveSession> activeSessions) {
        this.activeSessions = activeSessions == null ? new ArrayList<>() : new ArrayList<>(activeSessions);
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

    public static class ActiveSession {
        private String sessionId;
        private String clientLabel;
        private String provider;
        private String createdAtIso;
        private String lastSeenAtIso;
        private String mediaTokenHash;

        public String getMediaTokenHash() { return mediaTokenHash; }

        public void setMediaTokenHash(String mediaTokenHash) { this.mediaTokenHash = mediaTokenHash; }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getClientLabel() {
            return clientLabel;
        }

        public void setClientLabel(String clientLabel) {
            this.clientLabel = clientLabel;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getCreatedAtIso() {
            return createdAtIso;
        }

        public void setCreatedAtIso(String createdAtIso) {
            this.createdAtIso = createdAtIso;
        }

        public String getLastSeenAtIso() {
            return lastSeenAtIso;
        }

        public void setLastSeenAtIso(String lastSeenAtIso) {
            this.lastSeenAtIso = lastSeenAtIso;
        }
    }
}
