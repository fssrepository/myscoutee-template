package com.raxim.myscoutee.domain.accounts.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Completed-event relationship facts; display/profile data stays in the batched user read. */
@Document(collection = "userFriendships")
public record UserFriendship(
        @Id String id,
        @Indexed String leftUserId,
        @Indexed String rightUserId,
        Instant friendsSince,
        String firstEventId,
        Instant lastMetAt,
        String lastEventId,
        String lastEventName) {}
