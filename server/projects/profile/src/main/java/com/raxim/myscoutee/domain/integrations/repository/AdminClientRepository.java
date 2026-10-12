package com.raxim.myscoutee.domain.integrations.repository;

import org.bson.Document;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;

@Repository
public class AdminClientRepository {
    @org.springframework.beans.factory.annotation.Autowired private com.raxim.myscoutee.domain.chat.api.ChatDirectory chatDirectory;
    public String unreadVersion(String owner) {
        return chatDirectory.unreadVersion(owner);
    }
}
