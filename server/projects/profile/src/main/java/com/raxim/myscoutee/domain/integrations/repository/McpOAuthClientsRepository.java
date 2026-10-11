package com.raxim.myscoutee.domain.integrations.repository;

import java.time.Instant;
import org.springframework.data.mongodb.repository.MongoRepository;
import com.raxim.myscoutee.domain.integrations.document.McpOAuthClient;

public interface McpOAuthClientsRepository extends MongoRepository<McpOAuthClient, String> {
    long countByCreatedAtAfter(Instant since);
}
