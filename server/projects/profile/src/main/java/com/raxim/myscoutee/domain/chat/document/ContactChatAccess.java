package com.raxim.myscoutee.domain.chat.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Shared consent for a pair; saving a private contact never grants this permission. */
@Document(collection = "contactChatAccess")
public record ContactChatAccess(@Id String id, @Indexed String leftUserId, @Indexed String rightUserId,
        String requestedBy, String status, String requestedAtIso, String decidedAtIso, @Version Long version) implements com.raxim.myscoutee.domain.chat.api.ContactPermissions.Permission {
    public static String pairId(String first, String second) {
        return com.raxim.myscoutee.domain.chat.api.ContactPermissions.canonicalPairId(first, second);
    }
}
