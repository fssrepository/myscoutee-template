package com.raxim.myscoutee.common.config.mongo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;

/** Shared stream transport; domain owners retain queries, claims and effects. */
@Component
public class MongoChangeStreamService {
    private static final Logger LOG = LoggerFactory.getLogger(MongoChangeStreamService.class);
    private static final Duration COALESCE = Duration.ofMillis(25);
    private static final Duration RETRY = Duration.ofSeconds(5);
    private final MongoTemplate mongo;
    private final ScheduledMongoSessionRunner databases;
    private final Map<String, Subscription> subscriptions = new java.util.LinkedHashMap<>();
    private final Map<MongoSessionMode, Boolean> connected = new ConcurrentHashMap<>();
    private final Set<MongoCursor<?>> cursors = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService readers = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "mongo-change-reader"); thread.setDaemon(true); return thread;
    });
    private final ScheduledExecutorService workers = Executors.newScheduledThreadPool(2, task -> {
        Thread thread = new Thread(task, "mongo-change-work"); thread.setDaemon(true); return thread;
    });
    private volatile boolean running;

    static final class State {
        boolean dirty;
        boolean executing;
        Instant scheduledAt;
        ScheduledFuture<?> future;
    }

    static final class Subscription {
        final String name;
        final Set<String> collections;
        final Set<String> fields;
        final Supplier<Instant> action;
        final Runnable onDisconnect;
        final Runnable beforeWakeup;
        final Map<MongoSessionMode, State> states = new ConcurrentHashMap<>();
        Subscription(String name, Set<String> collections, Set<String> fields, Supplier<Instant> action, Runnable onDisconnect, Runnable beforeWakeup) {
            this.beforeWakeup = beforeWakeup;
            this.name = name; this.collections = Set.copyOf(collections); this.fields = Set.copyOf(fields); this.action = action; this.onDisconnect = onDisconnect;
        }
    }

    public MongoChangeStreamService(MongoTemplate mongo, ScheduledMongoSessionRunner databases) {
        this.mongo = mongo; this.databases = databases;
    }

    /**
     * Suppress empty scheduler passes using the existing stream, without caching
     * admin rules or deadlines. The owner must include future candidates and
     * committed recovery work, not just items that are due at query time.
     * Startup/reconnect reconciles after subscribing; a concurrent write marks
     * the subscription dirty and repeats the query before it goes back to sleep.
     */
    public java.util.function.BooleanSupplier watchPresence(String name, Set<String> collections,
            java.util.function.BooleanSupplier query) {
        Map<MongoSessionMode, Boolean> present = new ConcurrentHashMap<>();
        watch(name, collections, Set.of(), () -> {
            present.put(MongoSessionModeContext.currentMode(), query.getAsBoolean());
            return null;
        });
        return () -> {
            MongoSessionMode mode = databases.hasDistinctDemoDatabase()
                    ? MongoSessionModeContext.currentMode() : MongoSessionMode.REAL;
            return connected.getOrDefault(mode, false) && present.getOrDefault(mode, false);
        };
    }

    /** Register during bean initialization. Return the next known deadline, or null to sleep. */
    public synchronized void watch(String name, Set<String> collections, Set<String> fields, Supplier<Instant> action) {
        watch(name, collections, fields, action, () -> {});
    }

    public synchronized void watch(String name, Set<String> collections, Set<String> fields, Supplier<Instant> action, Runnable onDisconnect) {
        watch(name, collections, fields, action, onDisconnect, () -> {});
    }

    /** Invalidation runs before any affected worker is woken; it must not perform blocking I/O. */
    public synchronized void watch(String name, Set<String> collections, Set<String> fields, Supplier<Instant> action,
            Runnable onDisconnect, Runnable beforeWakeup) {
        if (running) throw new IllegalStateException("Register Mongo watchers before application readiness");
        if (collections.isEmpty() || subscriptions.containsKey(name)) throw new IllegalArgumentException("Invalid Mongo watcher: " + name);
        subscriptions.put(name, new Subscription(name, collections, fields, action, onDisconnect, beforeWakeup));
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running || subscriptions.isEmpty()) return;
        running = true;
        databases.runAcrossConfiguredDatabases(() -> {
            MongoSessionMode mode = MongoSessionModeContext.currentMode();
            readers.submit(() -> inMode(mode, () -> consume(mode)));
        });
    }

    private void consume(MongoSessionMode mode) {
        List<String> collections = subscriptions.values().stream().flatMap(s -> s.collections.stream()).distinct().toList();
        var filter = new Document("$match", new Document("$or", List.of(
                new Document("ns.coll", new Document("$in", collections)),
                new Document("operationType", new Document("$in", List.of("invalidate", "dropDatabase", "rename"))))));
        while (running && !Thread.currentThread().isInterrupted()) {
            try (var cursor = mongo.getDb().watch(List.of(filter)).maxAwaitTime(30, TimeUnit.SECONDS).iterator()) {
                cursors.add(cursor);
                try {
                    // Subscribe first. Writes racing with reconciliation remain in the cursor.
                    connected.put(mode, true);
                    reconcile(mode);
                    while (running && cursor.hasNext()) dispatch(mode, cursor.next());
                } finally {
                    cursors.remove(cursor);
                    connected.put(mode, false);
                }
            } catch (RuntimeException error) {
                connected.put(mode, false);
                if (running) LOG.warn("Mongo change stream for {} disconnected ({}); reconnect will reconcile.", mode, error.getClass().getSimpleName());
            }
            if (running) {
                for (Subscription subscription : subscriptions.values()) {
                    try { subscription.onDisconnect.run(); }
                    catch (RuntimeException failure) {
                        LOG.warn("Mongo disconnect hook {} failed ({}); reconnection continues.", subscription.name, failure.getClass().getSimpleName());
                    }
                }
                try { Thread.sleep(RETRY.toMillis()); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }
    }

    void reconcile(MongoSessionMode mode) {
        inMode(mode, () -> subscriptions.values().forEach(subscription -> subscription.beforeWakeup.run()));
        subscriptions.values().forEach(subscription -> signal(subscription, mode));
    }

    void dispatch(MongoSessionMode mode, ChangeStreamDocument<Document> event) {
        // A staging collection can be renamed over a watched collection. Its source
        // namespace is not the cache owner, so rename must reconcile all owners.
        String collection = event.getOperationType() == OperationType.RENAME || event.getNamespace() == null
                ? null : event.getNamespace().getCollectionName();
        var affected = subscriptions.values().stream().filter(subscription ->
                (collection == null || subscription.collections.contains(collection)) && relevant(subscription.fields, event)).toList();
        // Invalidate every affected cache before dispatching dependent work, regardless of registration order.
        inMode(mode, () -> affected.forEach(subscription -> subscription.beforeWakeup.run()));
        affected.forEach(subscription -> signal(subscription, mode));
    }

    static boolean relevant(Set<String> fields, ChangeStreamDocument<Document> event) {
        if (fields.isEmpty() || event.getOperationType() != OperationType.UPDATE) return true;
        var update = event.getUpdateDescription();
        if (update == null) return true;
        List<String> changed = new ArrayList<>(update.getUpdatedFields().keySet());
        changed.addAll(update.getRemovedFields());
        if (update.getTruncatedArrays() != null) update.getTruncatedArrays().forEach(value -> changed.add(value.getField()));
        return changed.stream().anyMatch(key -> fields.stream().anyMatch(field -> key.equals(field) || key.startsWith(field + ".") || field.startsWith(key + ".")));
    }

    private void signal(Subscription subscription, MongoSessionMode mode) {
        State state = subscription.states.computeIfAbsent(mode, ignored -> new State());
        synchronized (state) {
            state.dirty = true;
            if (!state.executing) schedule(subscription, mode, state, Instant.now().plus(COALESCE));
        }
    }

    private void schedule(Subscription subscription, MongoSessionMode mode, State state, Instant at) {
        if (!running || !connected.getOrDefault(mode, false)) return;
        if (state.future != null && !state.future.isDone() && !at.isBefore(state.scheduledAt)) return;
        if (state.future != null) state.future.cancel(false);
        state.scheduledAt = at;
        state.future = workers.schedule(() -> run(subscription, mode, state),
                Math.max(1, Duration.between(Instant.now(), at).toMillis()), TimeUnit.MILLISECONDS);
    }

    private void run(Subscription subscription, MongoSessionMode mode, State state) {
        synchronized (state) {
            state.future = null;
            if (!running || !connected.getOrDefault(mode, false)) return;
            state.executing = true;
            state.dirty = false;
        }
        Instant[] next = {null};
        try {
            inMode(mode, () -> next[0] = subscription.action.get());
        } catch (RuntimeException failure) {
            LOG.warn("Mongo-triggered work {} in {} failed ({}); retrying its outstanding wakeup.",
                    subscription.name, mode, failure.getClass().getSimpleName());
            next[0] = Instant.now().plus(RETRY);
        } finally {
            synchronized (state) {
                state.executing = false;
                if (state.dirty) schedule(subscription, mode, state, Instant.now().plus(COALESCE));
                else if (next[0] != null) schedule(subscription, mode, state, next[0]);
            }
        }
    }

    private static void inMode(MongoSessionMode mode, Runnable action) {
        MongoSessionMode previous = MongoSessionModeContext.currentMode();
        try { MongoSessionModeContext.set(mode); action.run(); }
        finally { MongoSessionModeContext.set(previous); }
    }

    @PreDestroy
    public void stop() {
        running = false;
        connected.clear();
        workers.shutdownNow();
        readers.shutdownNow();
        cursors.forEach(cursor -> {
            try { cursor.close(); } catch (RuntimeException ignored) { /* Client shutdown may already have closed it. */ }
        });
    }
}
