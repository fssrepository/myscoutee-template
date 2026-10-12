package com.raxim.myscoutee.domain.integrations.document;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Short-lived codes and rotating OAuth grants. Only digests of credentials persist. */
@Document("mcpAuthorizations")
public class McpAuthorization {
    @Id public String id;
    public String kind;
    @Indexed public String clientId;
    public String tokenId;
    public String ownerUserId;
    public String resource;
    public String redirectUri;
    public String challenge;
    @Indexed public String accessHash;
    @Indexed public String refreshHash;
    public List<String> usedRefreshHashes;
    public Instant accessExpiresAt;
    @Indexed(expireAfter = "0s") public Instant expiresAt;
    public boolean revoked;
}
