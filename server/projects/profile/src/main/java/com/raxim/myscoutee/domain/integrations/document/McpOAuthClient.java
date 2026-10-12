package com.raxim.myscoutee.domain.integrations.document;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Public client registration is metadata, never a user's permission grant. */
@Document("mcpOAuthClients")
public class McpOAuthClient {
    @Id public String id;
    public String name;
    public List<String> redirectUris;
    @Indexed public Instant createdAt;
    @Indexed(expireAfter = "0s") public Instant expiresAt;
}
