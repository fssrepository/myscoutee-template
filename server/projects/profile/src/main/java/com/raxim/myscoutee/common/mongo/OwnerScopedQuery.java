package com.raxim.myscoutee.common.mongo;

import java.util.*;
import java.util.function.Function;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

/** Apply an owning domain's batched audience selection before skip/limit, retaining query order. */
public final class OwnerScopedQuery {
    private static final int BATCH_SIZE = 1_000;
    private OwnerScopedQuery() {}
    public static <T> List<T> find(MongoTemplate mongo, Query query, Class<T> type,
            Function<T, String> ownerId, Function<Set<String>, Set<String>> selectOwners) {
        var scan = new BasicQuery(query.getQueryObject());
        scan.setSortObject(query.getSortObject());
        scan.cursorBatchSize(BATCH_SIZE);
        long remainingSkip = query.getSkip();
        int limit = query.getLimit();
        List<T> result = new ArrayList<>();
        try (var stream = mongo.stream(scan, type)) {
            var cursor = stream.iterator();
            while (cursor.hasNext()) {
                List<T> batch = new ArrayList<>(BATCH_SIZE);
                Set<String> owners = new LinkedHashSet<>();
                while (batch.size() < BATCH_SIZE && cursor.hasNext()) {
                    T value = cursor.next(); batch.add(value); owners.add(ownerId.apply(value));
                }
                Set<String> accepted = selectOwners.apply(owners);
                for (T value : batch) {
                    if (!accepted.contains(ownerId.apply(value))) continue;
                    if (remainingSkip > 0) { remainingSkip--; continue; }
                    result.add(value);
                    if (limit > 0 && result.size() >= limit) return result;
                }
            }
        }
        return result;
    }
}
