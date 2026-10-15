package com.raxim.myscoutee.domain.media.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("mediaDeletions")
@CompoundIndex(name="media_deletion_due_v1", def="{'baseGroupId':1,'status':1,'nextAttemptAt':1,'_id':1}")
public record MediaDeletion(@Id String id, String baseGroupId, String key, Instant createdAt,
        Instant nextAttemptAt, int attempts, String claim, String lastError, String status) {}
