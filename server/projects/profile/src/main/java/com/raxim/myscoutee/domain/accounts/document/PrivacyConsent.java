package com.raxim.myscoutee.domain.accounts.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "privacyConsents")
@CompoundIndexes({
        @CompoundIndex(name = "privacy_consent_user_revision", def = "{'userId': 1, 'revisionId': 1}", unique = true)
})
public class PrivacyConsent {
    @Id
    private String id;

    @Indexed(name = "privacy_consent_user")
    private String userId;

    @Indexed(name = "privacy_consent_revision")
    private String revisionId;

    private Integer revisionVersion;
    private List<String> approvedOptionalSectionIds = new ArrayList<>();
    private String acceptedAtIso;
    private String updatedAtIso;
    private String source;

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

    public String getRevisionId() {
        return revisionId;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public Integer getRevisionVersion() {
        return revisionVersion;
    }

    public void setRevisionVersion(Integer revisionVersion) {
        this.revisionVersion = revisionVersion;
    }

    public List<String> getApprovedOptionalSectionIds() {
        return approvedOptionalSectionIds;
    }

    public void setApprovedOptionalSectionIds(List<String> approvedOptionalSectionIds) {
        this.approvedOptionalSectionIds = approvedOptionalSectionIds == null
                ? new ArrayList<>()
                : new ArrayList<>(approvedOptionalSectionIds);
    }

    public String getAcceptedAtIso() {
        return acceptedAtIso;
    }

    public void setAcceptedAtIso(String acceptedAtIso) {
        this.acceptedAtIso = acceptedAtIso;
    }

    public String getUpdatedAtIso() {
        return updatedAtIso;
    }

    public void setUpdatedAtIso(String updatedAtIso) {
        this.updatedAtIso = updatedAtIso;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
