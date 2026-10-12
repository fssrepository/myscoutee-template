package com.raxim.myscoutee.domain.integrations.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "integrationTokens")
@CompoundIndexes({
        @CompoundIndex(name = "integration_token_owner_created", def = "{'ownerUserId': 1, 'createdAtIso': -1}")
})
public class IntegrationToken {
    @Id
    private String id;

    private String ownerUserId;
    private String name;
    private String scope;
    private String redirectUri;
    private String oauthClientId;
    public String getOauthClientId() { return oauthClientId; }
    public void setOauthClientId(String value) { oauthClientId = value; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String value) { redirectUri = value; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    @Indexed(name = "integration_token_hash", unique = true)
    private String tokenHash;

    private String tokenPrefix;
    private String createdAtIso;
    private String expiresAtIso;
    private String revokedAtIso;
    private String claimedClientIdHash;
    private String claimedAtIso;
    private String claimedAddress;
    private String lastUsedAtIso;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }
    public String getCreatedAtIso() { return createdAtIso; }
    public void setCreatedAtIso(String createdAtIso) { this.createdAtIso = createdAtIso; }
    public String getExpiresAtIso() { return expiresAtIso; }
    public void setExpiresAtIso(String expiresAtIso) { this.expiresAtIso = expiresAtIso; }
    public String getRevokedAtIso() { return revokedAtIso; }
    public void setRevokedAtIso(String revokedAtIso) { this.revokedAtIso = revokedAtIso; }
    public String getClaimedClientIdHash() { return claimedClientIdHash; }
    public void setClaimedClientIdHash(String claimedClientIdHash) { this.claimedClientIdHash = claimedClientIdHash; }
    public String getClaimedAtIso() { return claimedAtIso; }
    public void setClaimedAtIso(String claimedAtIso) { this.claimedAtIso = claimedAtIso; }
    public String getClaimedAddress() { return claimedAddress; }
    public void setClaimedAddress(String claimedAddress) { this.claimedAddress = claimedAddress; }
    public String getLastUsedAtIso() { return lastUsedAtIso; }
    public void setLastUsedAtIso(String lastUsedAtIso) { this.lastUsedAtIso = lastUsedAtIso; }
}
