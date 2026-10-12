package com.raxim.myscoutee.domain.chat.service;

import java.util.List;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import com.raxim.myscoutee.domain.chat.api.ChatMediaVisibility;
import com.raxim.myscoutee.domain.chat.dto.ChatMessageMediaVisibilityDto;

@Service
public class ChatMediaVisibilityService implements ChatMediaVisibility {
    private final MongoTemplate mongo;
    public ChatMediaVisibilityService(MongoTemplate mongo) { this.mongo = mongo; }
    @Override public List<ChatMessageMediaVisibilityDto> messages(List<String> urls) {
        Query query = Query.query(new Criteria().orOperator(Criteria.where("attachments.url").in(urls), Criteria.where("attachments.previewUrl").in(urls)));
        query.fields().include("chatId").include("deletedAtIso");
        return mongo.find(query, Document.class, "chatMessages").stream()
                .map(value -> new ChatMessageMediaVisibilityDto(value.getString("chatId"), value.getString("deletedAtIso"))).toList();
    }
}
