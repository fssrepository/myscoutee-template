package com.raxim.myscoutee.domain.content.service;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.*;
@org.springframework.stereotype.Service
public class ContentStatisticsService implements com.raxim.myscoutee.domain.content.api.ContentStatistics {
    private final MongoTemplate mongoTemplate;
    public ContentStatisticsService(MongoTemplate mongo) { mongoTemplate = mongo; }
    public long unresolvedReports() { return unresolved("userReports"); }
    public long unresolvedFeedback() { return unresolved("userFeedback"); }
    private long unresolved(String collection) { return mongoTemplate.count(Query.query(Criteria.where("resolvedAtIso").in(null, "")), collection); }
}
