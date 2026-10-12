package com.raxim.myscoutee.domain.integrations.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import com.raxim.myscoutee.domain.platform.dto.EncryptedSecret;

/** Protocol state only. The existing integration webhook queue owns deliveries. */
@Document("mcpEventSubscriptions")
@CompoundIndexes({
    @CompoundIndex(name = "mcp_events_owner_event", def = "{'ownerUserId':1,'name':1,'resourceId':1,'enabled':1}"),
    @CompoundIndex(name = "mcp_events_callback", def = "{'ownerUserId':1,'tokenId':1,'clientId':1,'callbackUrl':1}")
})
public class McpEventSubscription {
    @Id public String id;
    @Version public Long version;
    public String ownerUserId;
    public String tokenId;
    public String clientId;
    public String grantId;
    public String name;
    public String resourceId;
    public String callbackUrl;
    public String generation;
    public boolean enabled;
    public boolean truncated;
    public Instant expiresAt;
    // Keep disabled/expired records briefly for diagnostics; secrets never leave the server.
    @Indexed(expireAfter = "0s") public Instant deleteAfter;
    public Instant verifiedUntil;
    public String secretHash;
    public EncryptedSecret secret;
    public EncryptedSecret previousSecret;
    public Instant previousSecretUntil;
}
