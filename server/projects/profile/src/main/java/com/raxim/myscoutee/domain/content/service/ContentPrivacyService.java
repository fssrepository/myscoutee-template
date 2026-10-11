package com.raxim.myscoutee.domain.content.service;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.util.StringUtils;
@org.springframework.stereotype.Service
public class ContentPrivacyService implements com.raxim.myscoutee.domain.content.api.ContentPrivacy {
    private final MongoTemplate mongoTemplate;
    public ContentPrivacyService(MongoTemplate mongo) { mongoTemplate = mongo; }
    public void purgeFeedback(String userId) {
        deleteDocumentsByAnyField("userFeedback", List.of("userId", "createdUser", "updatedUser"), userId);
    }
    public void purgeReports(String userId) {
        deleteDocumentsByAnyField("userReports", List.of("userId", "targetUserId", "createdUser", "updatedUser"), userId);
    }
    private void deleteDocumentsByAnyField(String collectionName, List<String> fields, String userId) {
        if (!StringUtils.hasText(collectionName) || fields == null || fields.isEmpty() || !StringUtils.hasText(userId)) {
            return;
        }
        List<Criteria> criteria = fields.stream()
                .filter(StringUtils::hasText)
                .map(field -> Criteria.where(field).is(userId))
                .toList();
        if (criteria.isEmpty()) {
            return;
        }
        mongoTemplate.remove(Query.query(new Criteria().orOperator(criteria.toArray(Criteria[]::new))), collectionName);
    }

}
