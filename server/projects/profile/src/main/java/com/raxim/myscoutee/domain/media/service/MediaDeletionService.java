package com.raxim.myscoutee.domain.media.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.media.api.*;
import com.raxim.myscoutee.domain.media.config.MediaStorageProperties;
import com.raxim.myscoutee.domain.media.document.MediaDeletion;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;

/** Exact keys only: never enumerates a bucket or scans business collections. */
@Service
public class MediaDeletionService {
    static final int BATCH_SIZE = 100;
    static final int MAX_ATTEMPTS = 3;
    private static final Set<String> PERMANENT_ERRORS = Set.of("AccessDenied", "InvalidAccessKeyId", "SignatureDoesNotMatch",
            "InvalidArgument", "InvalidRequest", "InvalidBucketName", "NoSuchBucket", "MethodNotAllowed", "HTTP_400", "HTTP_401", "HTTP_403", "HTTP_404", "HTTP_405");
    private final MongoTemplate mongo;
    private final MediaObjectStoreClient objects;
    private final MediaStorageProperties properties;
    public MediaDeletionService(MongoTemplate mongo, MediaObjectStoreClient objects, MediaStorageProperties properties) {
        this.mongo = mongo; this.objects = objects; this.properties = properties;
    }
    public void enqueue(String group, List<String> references) {
        if (references == null || references.isEmpty() || references.size() > 100
                || group != null && (group.isBlank() || group.length() > 512)) throw new IllegalArgumentException("Invalid media deletion batch");
        // Validate the entire command before writing any intent.
        var keys = references.stream().flatMap(reference -> keys(reference).stream()).distinct().toList();
        var now = Instant.now();
        var bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, MediaDeletion.class);
        for (var item : keys) {
            String id = org.springframework.util.DigestUtils.md5DigestAsHex((String.valueOf(group) + "\n" + item).getBytes(StandardCharsets.UTF_8));
            bulk.upsert(Query.query(Criteria.where("_id").is(id)), new Update().setOnInsert("baseGroupId", group)
                    .setOnInsert("key", item).setOnInsert("createdAt", now).setOnInsert("nextAttemptAt", now).setOnInsert("attempts", 0)
                    .setOnInsert("status", "pending"));
        }
        bulk.execute();
    }
    static List<String> keys(String reference) {
        String key = MediaAccessPaths.extractPublicObjectKey(reference);
        if (!MediaAccessPaths.validKey(key)) throw new IllegalArgumentException("Invalid managed media reference");
        String image = MediaAccessPaths.managedImageSetGroupKeyFromObjectKey(key);
        if (image.isEmpty()) return List.of(key);
        var keys = new ArrayList<String>(); keys.add(image + "/original");
        for (var variant : MediaImageVariant.values()) keys.add(image + "/" + variant.objectName());
        return List.copyOf(keys);
    }
    public record Result(int deleted, int failed) {}
    public Result process(Instant now) {
        if (!objects.isEnabled()) throw new IllegalStateException("Media storage is unavailable");
        // A crash during the final request is an unknown outcome, not an unlimited new allowance.
        mongo.updateMulti(Query.query(Criteria.where("baseGroupId").is(JobGroupContext.groupId()).and("status").is("processing")
                        .and("attempts").gte(MAX_ATTEMPTS).and("nextAttemptAt").lte(now)),
                new Update().set("status", "failed").set("lastError", "OutcomeUnknown").unset("nextAttemptAt").unset("claim"), MediaDeletion.class);
        var due = Criteria.where("baseGroupId").is(JobGroupContext.groupId()).and("status").in("pending", "retry", "processing")
                .and("nextAttemptAt").lte(now).and("attempts").lt(MAX_ATTEMPTS);
        var selected = mongo.find(Query.query(due).with(Sort.by("nextAttemptAt", "_id")).limit(BATCH_SIZE), MediaDeletion.class);
        if (selected.isEmpty()) return new Result(0, 0);
        String claim = UUID.randomUUID().toString();
        var ids = selected.stream().map(MediaDeletion::id).toList();
        // A bounded lease prevents two worker replicas from owning the same batch.
        mongo.updateMulti(Query.query(Criteria.where("_id").in(ids).and("nextAttemptAt").lte(now).and("attempts").lt(MAX_ATTEMPTS)
                        .and("status").in("pending", "retry", "processing")),
                new Update().set("claim", claim).set("status", "processing").set("nextAttemptAt", now.plusSeconds(120)).inc("attempts", 1), MediaDeletion.class);
        var batch = mongo.find(Query.query(Criteria.where("_id").in(ids).and("claim").is(claim)), MediaDeletion.class);
        if (batch.isEmpty()) return new Result(0, 0);
        var allKeys = batch.stream().map(MediaDeletion::key).distinct().toList();
        Map<String, String> failed = new HashMap<>();
        try { for (var error : objects.deleteObjects(properties.getBucket(), allKeys)) failed.put(error.key(), error.code() == null ? "Unknown" : error.code()); }
        catch (RuntimeException unavailable) { allKeys.forEach(key -> failed.put(key, "OutcomeUnknown")); }
        var completed = new ArrayList<String>();
        record FailureState(String status, String code) {}
        var failures = new HashMap<FailureState, List<String>>();
        for (var item : batch) {
            String code = failed.get(item.key());
            if (code == null) { completed.add(item.id()); continue; }
            String status = item.attempts() >= MAX_ATTEMPTS || PERMANENT_ERRORS.contains(code) ? "failed" : "retry";
            failures.computeIfAbsent(new FailureState(status, code), ignored -> new ArrayList<>()).add(item.id());
        }
        if (!completed.isEmpty()) mongo.remove(Query.query(Criteria.where("_id").in(completed).and("claim").is(claim)), MediaDeletion.class);
        if (!failures.isEmpty()) {
            var bulk = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, MediaDeletion.class);
            failures.forEach((failure, affected) -> {
                var update = new Update().set("status", failure.status()).set("lastError", failure.code()).unset("claim");
                if ("retry".equals(failure.status())) update.set("nextAttemptAt", now.plusSeconds(60));
                else update.unset("nextAttemptAt");
                bulk.updateMulti(Query.query(Criteria.where("_id").in(affected).and("claim").is(claim)), update);
            });
            bulk.execute();
        }
        return new Result(completed.size(), batch.size() - completed.size());
    }
}
