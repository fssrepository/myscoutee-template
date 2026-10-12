package com.raxim.myscoutee.domain.chat.service;

import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.chat.api.ChatDirectory;
import com.raxim.myscoutee.domain.chat.repository.ChatsRepository;

@Service
public class ChatDirectoryService implements ChatDirectory {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.data.mongodb.core.MongoTemplate mongo;
    @Override public String unreadVersion(String owner) {
        Query query = Query.query(Criteria.where("ownerUserId").is(owner).and("unread").gt(0).and("lastSenderId").ne(owner))
                .with(Sort.by(Sort.Direction.DESC, "dateIso")).limit(1);
        query.fields().include("dateIso");
        Document row = mongo.findOne(query, Document.class, "chats");
        return row == null ? "" : row.getString("dateIso");
    }
    private final ChatsRepository chats;
    public ChatDirectoryService(ChatsRepository chats) { this.chats = chats; }
    @Override public Optional<com.raxim.myscoutee.domain.chat.document.Chat> findByOwnerUserIdAndChatId(String owner, String chat) { return chats.findByOwnerUserIdAndChatId(owner, chat); }
    @Override public List<com.raxim.myscoutee.domain.chat.document.Chat> findByChatId(String chat) { return chats.findByChatId(chat); }
    public java.util.Map<String, Integer> supportCaseCounts(String baseGroupId) {
        List<Document> rows = mongo.aggregate(
                org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                        context -> new Document("$match", new Document()
                                .append("channelType", new Document("$in", List.of("appSupport", "supportCase")))
                                .append("supportCase.status", new Document("$in", List.of("pending", "warned", "picked", "solved", "blocked")))
                                .append("supportBaseGroupId", baseGroupId)
                                .append("id", new Document("$type", "string").append("$ne", ""))),
                        context -> new Document("$group", new Document("_id", "$id")
                                .append("status", new Document("$first", "$supportCase.status"))),
                        context -> new Document("$group", new Document("_id", "$status")
                                .append("count", new Document("$sum", 1)))),
                com.raxim.myscoutee.domain.chat.document.Chat.class,
                Document.class)
                .getMappedResults();
        var result = new java.util.LinkedHashMap<String, Integer>();
        for (var row : rows) {
            Object count = row == null ? null : row.get("count");
            String status = row == null || row.getString("_id") == null ? "" : row.getString("_id").trim();
            result.put(status, count instanceof Number number ? Math.max(0, number.intValue()) : 0);
        }
        return result;
    }
}
