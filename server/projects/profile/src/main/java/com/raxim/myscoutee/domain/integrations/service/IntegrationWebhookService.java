package com.raxim.myscoutee.domain.integrations.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.raxim.myscoutee.domain.integrations.document.IntegrationWebhookDelivery;
import com.raxim.myscoutee.domain.integrations.repository.IntegrationWebhookDeliveriesRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/** Selected native role-attention producer and durable MCP callback worker. */
@Service
public class IntegrationWebhookService implements com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents {
    @org.springframework.beans.factory.annotation.Autowired private com.raxim.myscoutee.domain.accounts.api.AccountJobScopes accountJobScopes;
    @org.springframework.beans.factory.annotation.Autowired private com.raxim.myscoutee.domain.notifications.api.NotificationJobs groupRules;
    @org.springframework.beans.factory.annotation.Autowired private com.raxim.myscoutee.domain.platform.api.JobAudience jobGroupScope;
    @org.springframework.beans.factory.annotation.Autowired private com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner groupDatabases;
    @org.springframework.beans.factory.annotation.Autowired private McpEventsService mcpEvents;
    private static final List<Long> RETRY_SECONDS = List.of(5L, 30L);
    private static final String STATUS_PENDING = "PENDING", STATUS_RETRY = "RETRY", STATUS_SENDING = "SENDING", STATUS_SENT = "SENT", STATUS_FAILED = "FAILED";
    private final IntegrationWebhookDeliveriesRepository deliveriesRepository;
    private final MongoTemplate mongoTemplate;
    private final ExecutorService deliveryExecutor;
    private final Semaphore deliverySlots;
    private final int deliveryBatchSize;

    public IntegrationWebhookService(IntegrationWebhookDeliveriesRepository deliveriesRepository,
            MongoTemplate mongoTemplate,
            @Value("${integration.webhook.max-worker-count:100}") int maxWorkerCount,
            @Value("${integration.webhook.delivery-batch-size:100}") int deliveryBatchSize) {
        this.deliveriesRepository = deliveriesRepository;
        this.mongoTemplate = mongoTemplate;
        int effectiveMaxWorkerCount = Math.max(1, Math.min(maxWorkerCount, 100));
        this.deliveryBatchSize = Math.max(1, Math.min(deliveryBatchSize, 1_000));
        this.deliverySlots = new Semaphore(effectiveMaxWorkerCount);
        this.deliveryExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("integration-webhook-worker-", 0).factory());
    }

    @PreDestroy
    void shutdownDeliveryWorkers() {
        deliveryExecutor.shutdownNow();
    }

    @PostConstruct
    void recoverInterruptedDeliveries() {
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("status").is(STATUS_SENDING).and("nextAttemptAtIso").lte(Instant.now().toString())),
                new Update()
                        .set("status", STATUS_RETRY)
                        .set("nextAttemptAtIso", Instant.now().toString()),
                IntegrationWebhookDelivery.class);
    }

    @org.springframework.beans.factory.annotation.Autowired
    void watchDeliveryQueue(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes) {
        changes.watch("integration-webhooks", java.util.Set.of("integrationWebhookDeliveries"),
                java.util.Set.of("status", "nextAttemptAtIso"), () -> {
                    deliverPendingInCurrentDatabase();
                    Query nextQuery = Query.query(Criteria.where("status").in(STATUS_PENDING, STATUS_RETRY, STATUS_SENDING))
                            .with(Sort.by("nextAttemptAtIso")).limit(1);
                    nextQuery.fields().include("nextAttemptAtIso");
                    var next = mongoTemplate.findOne(nextQuery, IntegrationWebhookDelivery.class);
                    if (next == null) return null;
                    Instant retry = Instant.now().plusSeconds(1);
                    Instant due = StringUtils.hasText(next.getNextAttemptAtIso()) ? Instant.parse(next.getNextAttemptAtIso()) : retry;
                    return due.isAfter(retry) ? due : retry;
                });
    }

    public void deliverPending() {
        groupDatabases.runAcrossConfiguredDatabases(this::deliverPendingInCurrentDatabase);
    }

    private void deliverPendingInCurrentDatabase() {
        // One indexed read per database before any per-workspace remote calls or
            // job history writes. Include expired leases so recovery still wakes up.
            if (!mongoTemplate.exists(Query.query(Criteria.where("status")
                    .in(STATUS_PENDING, STATUS_RETRY, STATUS_SENDING)
                    .and("nextAttemptAtIso").lte(Instant.now().toString())), IntegrationWebhookDelivery.class)) {
                return;
            }
            groupRules.runScheduledGroups("integration-webhooks", this::deliverPendingInGroup);
    }

    private void deliverPendingInGroup() {
        int availableSlots = deliverySlots.availablePermits();
        if (availableSlots <= 0) {
            return;
        }
        String now = Instant.now().toString();
        // A lease recovers interrupted work in every configured database, not just the startup DB.
        mongoTemplate.updateMulti(Query.query(Criteria.where("status").is(STATUS_SENDING)
                        .and("nextAttemptAtIso").lte(now)),
                new Update().set("status", STATUS_RETRY), IntegrationWebhookDelivery.class);
        Query due = Query.query(new Criteria().andOperator(
                Criteria.where("status").in(STATUS_PENDING, STATUS_RETRY),
                Criteria.where("nextAttemptAtIso").lte(now)));
        due.with(Sort.by(Sort.Order.asc("createdAtIso")));
        due.limit(Math.min(deliveryBatchSize, availableSlots));
        var workspaceScope = jobGroupScope.workspaceScope();
        for (IntegrationWebhookDelivery candidate : com.raxim.myscoutee.common.mongo.OwnerScopedQuery.find(mongoTemplate, due, IntegrationWebhookDelivery.class, IntegrationWebhookDelivery::getOwnerUserId, ids -> accountJobScopes.matchingOwners(ids, workspaceScope))) {
            var permit = McpEventsService.PROTOCOL.equals(candidate.getProtocol()) ? mcpEvents.reserveDelivery() : null;
            if (McpEventsService.PROTOCOL.equals(candidate.getProtocol()) && permit == null) continue;
            if (!deliverySlots.tryAcquire()) {
                if (permit != null) permit.close();
                break;
            }
            IntegrationWebhookDelivery claimed;
            try {
                claimed = mongoTemplate.findAndModify(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("_id").is(candidate.getId()),
                            Criteria.where("status").in(STATUS_PENDING, STATUS_RETRY),
                            Criteria.where("nextAttemptAtIso").lte(now))),
                    new Update().set("status", STATUS_SENDING).set("nextAttemptAtIso", Instant.now().plusSeconds(60).toString()),
                    FindAndModifyOptions.options().returnNew(true),
                    IntegrationWebhookDelivery.class);
            } catch (RuntimeException error) {
                deliverySlots.release(); if (permit != null) permit.close(); throw error;
            }
            if (claimed == null) { deliverySlots.release(); if (permit != null) permit.close(); }
            if (claimed != null) {
                try {
                    var groupId = com.raxim.myscoutee.domain.platform.api.JobGroupContext.groupId();
                    var mode = com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode();
                    deliveryExecutor.execute(() -> {
                        var previousMode = com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode();
                        try {
                            com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.set(mode);
                            com.raxim.myscoutee.domain.platform.api.JobGroupContext.run(groupId, () -> deliver(claimed, permit));
                        } finally {
                            com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.set(previousMode);
                            deliverySlots.release();
                            if (permit != null) permit.close();
                        }
                    });
                } catch (RejectedExecutionException exception) {
                    deliverySlots.release();
                    if (permit != null) permit.close();
                    claimed.setStatus(STATUS_RETRY);
                    claimed.setNextAttemptAtIso(Instant.now().toString());
                    claimed.setLastError("worker_pool_busy");
                    deliveriesRepository.save(claimed);
                    break;
                }
            }
        }
    }

    @Override
    public void enqueueRoleAttention(RoleAttention notification) {
        String name = switch (String.valueOf(notification.entityType())) {
            case "operator" -> McpEventsService.OPERATOR_ATTENTION;
            default -> null;
        };
        if (name == null || !McpEventsService.roleKinds(name).contains(notification.notificationType())) return;
        for (String owner : notification.recipientUserIds()) {
            for (var delivery : mcpEvents.deliveries(owner, name, notification.entityType(), "node",
                    Map.of("kind", notification.notificationType()), notification.id())) {
                try { deliveriesRepository.insert(delivery); }
                catch (DuplicateKeyException alreadyQueued) { /* Preserve prior delivery and retry state. */ }
            }
        }
    }

    private void deliver(IntegrationWebhookDelivery delivery, McpEventCallbackClient.DeliveryPermit permit) {
        if (McpEventsService.PROTOCOL.equals(delivery.getProtocol())) {
            try {
                int status = mcpEvents.deliver(delivery, permit);
                if (status >= 200 && status < 300) {
                    delivery.setStatus(STATUS_SENT); delivery.setSentAtIso(Instant.now().toString());
                    delivery.setLastError(null); deliveriesRepository.save(delivery);
                } else if (status == 410 || status == 413 || (status >= 400 && status < 500 && status != 429)) {
                    markFailed(delivery, "http_" + status);
                } else scheduleRetry(delivery, "http_" + status);
            } catch (McpEventCallbackClient.Throttled throttled) {
                // Local backpressure is scheduling, not a failed remote attempt.
                delivery.setStatus(STATUS_RETRY);
                delivery.setNextAttemptAtIso(Instant.now().plusSeconds(1).toString());
                delivery.setLastError("throttled"); deliveriesRepository.save(delivery);
            } catch (RuntimeException | java.io.IOException failure) {
                scheduleRetry(delivery, failure.getClass().getSimpleName());
            }
            return;
        }
        markFailed(delivery, "unsupported_protocol");
    }

    private void scheduleRetry(IntegrationWebhookDelivery delivery, String error) {
        int attempts = safeNumber(delivery.getAttemptCount()) + 1;
        delivery.setAttemptCount(attempts);
        delivery.setLastError(shortText(error));
        if (attempts > RETRY_SECONDS.size()) {
            delivery.setStatus(STATUS_FAILED);
            delivery.setNextAttemptAtIso(null);
        } else {
            delivery.setStatus(STATUS_RETRY);
            delivery.setNextAttemptAtIso(Instant.now()
                    .plus(RETRY_SECONDS.get(attempts - 1), ChronoUnit.SECONDS)
                    .toString());
        }
        deliveriesRepository.save(delivery);
    }

    private void markFailed(IntegrationWebhookDelivery delivery, String error) {
        delivery.setStatus(STATUS_FAILED);
        delivery.setLastError(shortText(error));
        delivery.setNextAttemptAtIso(null);
        deliveriesRepository.save(delivery);
    }

    private String shortText(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return "delivery_failed";
        }
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private int safeNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
