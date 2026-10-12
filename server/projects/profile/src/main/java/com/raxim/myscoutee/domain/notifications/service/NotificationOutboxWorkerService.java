package com.raxim.myscoutee.domain.notifications.service;

import java.time.Duration;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery.PushMessage;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery.PushMulticast;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery.WebPush;
import com.mongodb.client.result.UpdateResult;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner;
import com.raxim.myscoutee.domain.platform.api.AuthenticationMode;
import com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications.Recipient;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications.Device;
import com.raxim.myscoutee.domain.notifications.repository.NotificationOutboxRepository;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;
import com.raxim.myscoutee.domain.chat.api.ChatPresence;
import com.raxim.myscoutee.domain.platform.api.NotificationPolicy;
import com.raxim.myscoutee.common.redis.RedisStreamRetentionService;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery.FirebaseBatchSendResult;
import com.raxim.myscoutee.domain.platform.api.FirebasePushDelivery.FirebaseTokenSendResult;
import com.raxim.myscoutee.domain.platform.api.DeploymentBranding;
import com.raxim.myscoutee.domain.platform.api.DeploymentBranding.ResolvedDeploymentBranding;

@Service
public class NotificationOutboxWorkerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationOutboxWorkerService.class);
    private static final String DELIVERY_STRATEGY_DIRECT = "direct";
    private static final String DELIVERY_STRATEGY_TOPIC = "topic";

    private final AuthenticationMode configProperties;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final AccountNotifications accounts;
    private final ChatPresence chatWebSocketSessionRegistry;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationPolicy platformParametersService;
    private final RedisStreamRetentionService redisStreamRetentionService;
    private final OperationalTelemetry adminTelemetryService;
    private final FirebasePushDelivery firebaseMessagingGateway;
    private final int batchSize;
    private final int firebaseChunkSize;
    private final int maxRetries;
    private final Duration leaseDuration;
    private final Duration retryBaseDelay;
    private final int deliveryWindowStartHour;
    private final int deliveryWindowEndHour;
    private ObjectProvider<NotificationRuleRegistryService> notificationRuleRegistryServiceProvider;
    private ScheduledMongoSessionRunner scheduledMongoSessionRunner;
    private final java.util.Map<String, String> lastWakeupStreamIds = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Instant lastScheduledPollAt = Instant.EPOCH;

    public NotificationOutboxWorkerService(
            AuthenticationMode configProperties,
            NotificationOutboxRepository notificationOutboxRepository,
            AccountNotifications accounts,
            ChatPresence chatWebSocketSessionRegistry,
            MongoTemplate mongoTemplate,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            NotificationPolicy platformParametersService,
            OperationalTelemetry adminTelemetryService,
            FirebasePushDelivery firebaseMessagingGateway,
            @Value("${notifications.outbox.batch-size:50}") int batchSize,
            @Value("${notifications.outbox.firebase-chunk-size:500}") int firebaseChunkSize,
            @Value("${notifications.outbox.max-retries:5}") int maxRetries,
            @Value("${notifications.outbox.lease-ms:300000}") long leaseMs,
            @Value("${notifications.outbox.retry-base-ms:60000}") long retryBaseMs,
            @Value("${notifications.outbox.delivery-window-start-hour:0}") int deliveryWindowStartHour,
            @Value("${notifications.outbox.delivery-window-end-hour:24}") int deliveryWindowEndHour,
            RedisStreamRetentionService redisStreamRetentionService) {
        this.configProperties = configProperties;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.accounts = accounts;
        this.chatWebSocketSessionRegistry = chatWebSocketSessionRegistry;
        this.mongoTemplate = mongoTemplate;
        this.stringRedisTemplate = stringRedisTemplateProvider == null ? null : stringRedisTemplateProvider.getIfAvailable();
        this.platformParametersService = platformParametersService;
        this.redisStreamRetentionService = redisStreamRetentionService;
        this.adminTelemetryService = adminTelemetryService;
        this.firebaseMessagingGateway = firebaseMessagingGateway;
        this.batchSize = Math.max(1, Math.min(500, batchSize));
        this.firebaseChunkSize = Math.max(1, Math.min(500, firebaseChunkSize));
        this.maxRetries = Math.max(1, maxRetries);
        this.leaseDuration = Duration.ofMillis(Math.max(1000L, leaseMs));
        this.retryBaseDelay = Duration.ofMillis(Math.max(1000L, retryBaseMs));
        this.deliveryWindowStartHour = clampHour(deliveryWindowStartHour, 0);
        this.deliveryWindowEndHour = clampHour(deliveryWindowEndHour, 24);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setNotificationRuleRegistryServiceProvider(
            ObjectProvider<NotificationRuleRegistryService> notificationRuleRegistryServiceProvider) {
        this.notificationRuleRegistryServiceProvider = notificationRuleRegistryServiceProvider;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setScheduledMongoSessionRunner(ScheduledMongoSessionRunner scheduledMongoSessionRunner) {
        this.scheduledMongoSessionRunner = scheduledMongoSessionRunner;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void watchOutbox(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes) {
        changes.watch("notification-outbox", java.util.Set.of("notificationOutbox", "notificationRuleConfigs"),
                java.util.Set.of("status", "nextRetryAt", "lockedUntil", "firebaseDeliveryRequested",
                        "enabled", "timing", "scheduleSlots", "parameters"), () -> {
                    processDueNotificationsForCurrentDatabase();
                    return nextOutboxWakeup();
                });
    }

    private Instant nextOutboxWakeup() {
        NotificationRuleRegistryService registry = notificationRuleRegistry();
        if (registry == null) return null;
        Instant[] earliest = {null};
        registry.forEachGroup(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX, () -> {
            var rule = registry.findRule(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX).orElse(null);
            if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) return;
            Instant minimum = Instant.now().plusSeconds(5);
            boolean insideWindow = isInsideDeliveryWindow();
            for (boolean push : new boolean[] {false, true}) {
                var match = new org.bson.Document("baseGroupId", JobGroupContext.groupId())
                        .append("status", NotificationOutboxItem.STATUS_PENDING)
                        .append("firebaseDeliveryRequested", push
                                ? new org.bson.Document("$ne", false) : false);
                if (push && !configProperties.isFirebaseEnabled()) {
                    match.put("firebaseDeliveryRequested", true);
                }
                var epoch = java.util.Date.from(Instant.EPOCH);
                var due = new org.bson.Document("$max", java.util.List.of(
                        new org.bson.Document("$convert", new org.bson.Document("input", "$nextRetryAt").append("to", "date").append("onNull", epoch).append("onError", epoch)),
                        new org.bson.Document("$convert", new org.bson.Document("input", "$lockedUntil").append("to", "date").append("onNull", epoch).append("onError", epoch))));
                var next = mongoTemplate.getCollection(mongoTemplate.getCollectionName(NotificationOutboxItem.class)).aggregate(java.util.List.of(
                        new org.bson.Document("$match", match), new org.bson.Document("$project", new org.bson.Document("due", due)),
                        new org.bson.Document("$sort", new org.bson.Document("due", 1)), new org.bson.Document("$limit", 1))).first();
                if (next == null) continue;
                Instant at = next.getDate("due").toInstant();
                if (at.isBefore(minimum)) at = minimum;
                if (push && !insideWindow) {
                    var now = java.time.ZonedDateTime.now(ZoneId.systemDefault());
                    int start = effectiveDeliveryWindowStartHour();
                    var opening = now.toLocalDate().atStartOfDay(now.getZone()).plusHours(start);
                    if (!opening.isAfter(now)) opening = opening.plusDays(1);
                    if (at.isBefore(opening.toInstant())) at = opening.toInstant();
                }
                if (earliest[0] == null || at.isBefore(earliest[0])) earliest[0] = at;
            }
        });
        return earliest[0];
    }

    public void processDueNotifications() {
        if (scheduledMongoSessionRunner == null) {
            processDueNotificationsForCurrentDatabase();
            return;
        }
        scheduledMongoSessionRunner.runAcrossConfiguredDatabases(this::processDueNotificationsForCurrentDatabase);
    }

    private void processDueNotificationsForCurrentDatabase() {
        var registry = notificationRuleRegistry();
        if (registry != null) registry.forEachGroup(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX, this::processDueNotificationsForCurrentDatabaseInGroup);
    }

    private void processDueNotificationsForCurrentDatabaseInGroup() {
        refreshPendingCount();
        Instant startedAt = Instant.now();
        if (!mongoTemplate.exists(dueQuery(startedAt.toString()), NotificationOutboxItem.class)) {
            return;
        }
        NotificationRuleRegistryService registry = notificationRuleRegistry();
        boolean ruleConfigured = registry != null
                && registry.findRule(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX).isPresent();
        boolean due = ruleConfigured
                ? registry.shouldRunScheduledRule(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX, startedAt)
                : claimScheduledPoll(startedAt);
        if (!due) {
            return;
        }
        if (ruleConfigured && registry.beginScheduledRun(
                    NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX,
                    "Scheduled notification outbox delivery started.",
                    "notification-outbox",
                    startedAt) == null) {
            return;
        }
        try {
            int processedCount = processDueNotificationsNow();
            if (ruleConfigured) {
                String detail = "Processed pending notification outbox delivery decisions.";
                registry.recordRun(
                        NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX,
                        processedCount,
                        "completed",
                        detail,
                        "notification-outbox",
                        startedAt);
            }
        } catch (RuntimeException exception) {
            if (ruleConfigured) {
                registry.recordRun(
                        NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX,
                        0,
                        "failed",
                        safeText(exception.getMessage()),
                        "notification-outbox",
                        startedAt);
            }
            throw exception;
        }
    }

    private int processDueNotificationsNow() {
        boolean insideDeliveryWindow = isInsideDeliveryWindow();
        String nowIso = Instant.now().toString();
        List<NotificationOutboxItem> leasedItems = new ArrayList<>();
        for (NotificationOutboxItem item : dueItems(nowIso, effectiveBatchSize())) {
            if (!insideDeliveryWindow && !Boolean.FALSE.equals(item.getFirebaseDeliveryRequested())) continue;
            // Only pre-snapshot records retain the former live-config gate.
            if (item.getFirebaseDeliveryRequested() == null && !configProperties.isFirebaseEnabled()) continue;
            NotificationOutboxItem leased = lease(item, nowIso).orElse(null);
            if (leased == null) {
                continue;
            }
            leasedItems.add(leased);
        }
        processLeasedItems(leasedItems);
        return leasedItems.size();
    }

    private void processLeasedItems(List<NotificationOutboxItem> leasedItems) {
        if (leasedItems == null || leasedItems.isEmpty()) {
            return;
        }
        int workerCount = Math.min(effectiveMaxWorkers(), leasedItems.size());
        if (workerCount <= 1) {
            leasedItems.forEach(this::processLeasedItem);
            return;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(workerCount);
        MongoSessionMode sessionMode = MongoSessionModeContext.currentMode();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (NotificationOutboxItem leased : leasedItems) {
                futures.add(executorService.submit(
                        () -> withMongoSessionMode(sessionMode, () -> processLeasedItem(leased))));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception exception) {
                    LOGGER.warn("Notification outbox worker task failed.", exception);
                }
            }
        } finally {
            executorService.shutdown();
        }
    }

    private void withMongoSessionMode(MongoSessionMode sessionMode, Runnable action) {
        MongoSessionMode previousMode = MongoSessionModeContext.currentMode();
        try {
            MongoSessionModeContext.set(sessionMode);
            action.run();
        } finally {
            MongoSessionModeContext.set(previousMode);
        }
    }

    private void processLeasedItem(NotificationOutboxItem leased) {
        if (leased != null) JobGroupContext.run(leased.getBaseGroupId(), () -> processLeasedItemInGroup(leased));
    }

    private void processLeasedItemInGroup(NotificationOutboxItem leased) {
        if (leased == null) {
            return;
        }
        try {
            DeliveryResult result = deliver(leased);
            markSent(leased, result, Instant.now().toString());
        } catch (Exception exception) {
            markFailed(leased, exception, Instant.now());
        }
    }

    @SuppressWarnings("unchecked")
    public void processRedisWakeups() {
        Runnable action = () -> {
            var registry = notificationRuleRegistry();
            if (registry != null) registry.forEachGroup(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX, this::processRedisWakeupsInGroup);
        };
        if (scheduledMongoSessionRunner == null) action.run();
        else scheduledMongoSessionRunner.runAcrossConfiguredDatabases(action);
    }

    private void processRedisWakeupsInGroup() {
        if (stringRedisTemplate == null) {
            return;
        }
        NotificationRuleRegistryService registry = notificationRuleRegistry();
        if (registry != null
                && registry.findRule(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX).isPresent()
                && !registry.findRule(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX)
                        .map(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                        .orElse(false)) {
            return;
        }
        Instant startedAt = null;
        boolean runStarted = false;
        try {
            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                    StreamReadOptions.empty().count(effectiveBatchSize()),
                    StreamOffset.create(NotificationOutboxService.wakeupStreamKey(), ReadOffset.from(lastWakeupStreamIds.getOrDefault(NotificationOutboxService.wakeupStreamKey(), "0-0"))));
            if (records == null || records.isEmpty()) {
                return;
            }
            List<String> recordIds = records.stream()
                    .map(record -> record.getId().getValue())
                    .filter(StringUtils::hasText)
                    .toList();
            lastWakeupStreamIds.put(NotificationOutboxService.wakeupStreamKey(), records.get(records.size() - 1).getId().getValue());
            startedAt = Instant.now();
            boolean ruleConfigured = registry != null
                    && registry.findRule(NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX).isPresent();
            if (ruleConfigured && registry.beginRun(
                        NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX,
                        "Notification outbox wake-up started.",
                        "notification-outbox",
                        startedAt) == null) {
                return;
            }
            runStarted = ruleConfigured;
            int processedCount = processDueNotificationsNow();
            if (ruleConfigured) {
                registry.recordRun(
                        NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX,
                        processedCount,
                        "completed",
                        "Processed notification outbox wake-up work.",
                        "notification-outbox",
                        startedAt);
            }
            redisStreamRetentionService.deleteRecords(stringRedisTemplate, NotificationOutboxService.wakeupStreamKey(), recordIds);
        } catch (RuntimeException exception) {
            if (runStarted && registry != null && startedAt != null) {
                registry.recordRun(
                        NotificationRuleRegistryService.RULE_NOTIFICATION_OUTBOX,
                        0,
                        "failed",
                        safeText(exception.getMessage()),
                        "notification-outbox",
                        startedAt);
            }
            LOGGER.debug("Notification outbox Redis wakeup read failed; scheduled polling remains active.", exception);
        }
    }

    private synchronized boolean claimScheduledPoll(Instant now) {
        Instant effectiveNow = now == null ? Instant.now() : now;
        long delayMs = platformParametersService == null
                ? 60_000L
                : platformParametersService.notificationOutboxPollDelayMs(60_000L);
        if (lastScheduledPollAt != Instant.EPOCH
                && effectiveNow.toEpochMilli() - lastScheduledPollAt.toEpochMilli() < delayMs) {
            return false;
        }
        lastScheduledPollAt = effectiveNow;
        return true;
    }

    private Query dueQuery(String nowIso) {
        Criteria statusCriteria = Criteria.where("status").is(NotificationOutboxItem.STATUS_PENDING);
        Criteria retryCriteria = new Criteria().orOperator(
                Criteria.where("nextRetryAt").exists(false),
                Criteria.where("nextRetryAt").is(null),
                Criteria.where("nextRetryAt").lte(nowIso));
        return Query.query(new Criteria().andOperator(statusCriteria, retryCriteria, Criteria.where("baseGroupId").is(JobGroupContext.groupId())));
    }

    private List<NotificationOutboxItem> dueItems(String nowIso, int limit) {
        Query query = dueQuery(nowIso)
                .with(Sort.by(Sort.Direction.ASC, "createdDate"))
                .limit(Math.max(1, limit));
        if (!configProperties.isFirebaseEnabled()) {
            query.addCriteria(Criteria.where("firebaseDeliveryRequested").ne(null));
        }
        return mongoTemplate.find(query, NotificationOutboxItem.class);
    }

    private java.util.Optional<NotificationOutboxItem> lease(NotificationOutboxItem item, String nowIso) {
        if (item == null || !StringUtils.hasText(safeText(item.getId()))) {
            return java.util.Optional.empty();
        }
        String lockedUntil = Instant.now().plus(leaseDuration).toString();
        Criteria leaseCriteria = new Criteria().orOperator(
                Criteria.where("lockedUntil").exists(false),
                Criteria.where("lockedUntil").is(null),
                Criteria.where("lockedUntil").lte(nowIso));
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("_id").is(item.getId()),
                Criteria.where("status").is(NotificationOutboxItem.STATUS_PENDING),
                leaseCriteria);
        Update update = new Update()
                .set("status", NotificationOutboxItem.STATUS_RUNNING)
                .set("startedDate", nowIso)
                .set("lockedUntil", lockedUntil)
                .set("updatedDate", nowIso);
        UpdateResult result = mongoTemplate.updateFirst(Query.query(criteria), update, NotificationOutboxItem.class);
        if (result.getMatchedCount() <= 0) {
            return java.util.Optional.empty();
        }
        return notificationOutboxRepository.findById(item.getId());
    }

    private DeliveryResult deliver(NotificationOutboxItem item) {
        if (Boolean.FALSE.equals(item.getFirebaseDeliveryRequested())) {
            return new DeliveryResult(0, 0, Set.of());
        }
        if (DELIVERY_STRATEGY_TOPIC.equals(safeText(item.getDeliveryStrategy())) && StringUtils.hasText(safeText(item.getTopicName()))) {
            firebaseMessagingGateway.send(buildTopicMessage(item, safeText(item.getTopicName())));
            return new DeliveryResult(1, 0, Set.of());
        }
        List<String> recipientUserIds = deliveryRecipientUserIds(item);
        if (recipientUserIds.isEmpty()) {
            return new DeliveryResult(0, 0, Set.of());
        }
        List<Recipient> users = accounts.recipients(recipientUserIds);
        List<TokenRegistration> registrations = tokenRegistrations(item, users);
        if (registrations.isEmpty()) {
            return new DeliveryResult(0, 0, Set.of());
        }
        DeliveryResult result = DELIVERY_STRATEGY_DIRECT.equals(safeText(item.getDeliveryStrategy()))
                ? deliverDirect(item, registrations)
                : deliverMulticast(item, registrations);
        removeInvalidTokens(users, result.invalidTokens());
        return result;
    }

    List<String> deliveryRecipientUserIds(NotificationOutboxItem item) {
        if (item != null && item.getFirebaseDeliveryRequested() != null) {
            return item.getFirebaseDevices() == null ? List.of() : item.getFirebaseDevices().entrySet().stream()
                    .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                    .map(Map.Entry::getKey).toList();
        }
        List<String> recipientUserIds = compactIds(item == null ? null : item.getRecipientUserIds());
        if (item == null
                || !"chat-message".equals(safeText(item.getNotificationType()))
                || chatWebSocketSessionRegistry == null) {
            return recipientUserIds;
        }
        String chatId = firstNonBlank(
                item.getEntityId(),
                item.getPayload() == null ? null : item.getPayload().get("chatId"));
        if (!StringUtils.hasText(chatId)) {
            return recipientUserIds;
        }
        return recipientUserIds.stream()
                .filter(userId -> !chatWebSocketSessionRegistry.isUserConnected(chatId, userId))
                .toList();
    }

    private DeliveryResult deliverDirect(NotificationOutboxItem item, List<TokenRegistration> registrations) {
        int success = 0;
        int failure = 0;
        LinkedHashSet<String> invalidTokens = new LinkedHashSet<>();
        for (int index = 0; index < registrations.size(); index += firebaseChunkSize) {
            List<TokenRegistration> chunk = registrations.subList(index, Math.min(index + firebaseChunkSize, registrations.size()));
            List<PushMessage> messages = chunk.stream()
                    .map(registration -> buildTokenMessage(item, registration.token()))
                    .toList();
            FirebaseBatchSendResult response = firebaseMessagingGateway.sendAll(
                    messages,
                    chunk.stream().map(TokenRegistration::token).toList());
            success += response.successCount();
            failure += response.failureCount();
            collectInvalidTokens(chunk, response, invalidTokens);
        }
        return new DeliveryResult(success, failure, invalidTokens);
    }

    private DeliveryResult deliverMulticast(NotificationOutboxItem item, List<TokenRegistration> registrations) {
        int success = 0;
        int failure = 0;
        LinkedHashSet<String> invalidTokens = new LinkedHashSet<>();
        for (int index = 0; index < registrations.size(); index += firebaseChunkSize) {
            List<TokenRegistration> chunk = registrations.subList(index, Math.min(index + firebaseChunkSize, registrations.size()));
            List<String> tokens = chunk.stream().map(TokenRegistration::token).toList();
            FirebaseBatchSendResult response = firebaseMessagingGateway.sendMulticast(buildMessage(item, tokens), tokens);
            success += response.successCount();
            failure += response.failureCount();
            collectInvalidTokens(chunk, response, invalidTokens);
        }
        return new DeliveryResult(success, failure, invalidTokens);
    }

    private void collectInvalidTokens(
            List<TokenRegistration> chunk,
            FirebaseBatchSendResult response,
            LinkedHashSet<String> invalidTokens) {
        List<FirebaseTokenSendResult> responses = response == null ? List.of() : response.responses();
        for (int responseIndex = 0; responseIndex < responses.size() && responseIndex < chunk.size(); responseIndex += 1) {
            FirebaseTokenSendResult sendResponse = responses.get(responseIndex);
            if (sendResponse != null && !sendResponse.successful() && isInvalidToken(sendResponse.errorCode())) {
                invalidTokens.add(chunk.get(responseIndex).token());
            }
        }
    }

    private PushMulticast buildMessage(NotificationOutboxItem item, List<String> tokens) {
        Map<String, String> payload = payloadFor(item);
        return new PushMulticast(tokens, payload, webpushConfig(item, payload));
    }

    private PushMessage buildTokenMessage(NotificationOutboxItem item, String token) {
        Map<String, String> payload = payloadFor(item);
        return new PushMessage(token, null, payload, webpushConfig(item, payload));
    }

    private PushMessage buildTopicMessage(NotificationOutboxItem item, String topicName) {
        Map<String, String> payload = payloadFor(item);
        return new PushMessage(null, topicName, payload, webpushConfig(item, payload));
    }

    Map<String, String> payloadFor(NotificationOutboxItem item) {
        Map<String, String> payload = new LinkedHashMap<>(item.getPayload() == null ? Map.of() : item.getPayload());
        ResolvedDeploymentBranding branding =
                snapshottedBranding(payload);
        putIfText(payload, "type", item.getNotificationType());
        putIfText(payload, "title", firstNonBlank(
                item.getTitle(),
                payload.get("title"),
                branding.productName()));
        putIfText(payload, "body", item.getBody());
        putIfText(payload, "url", firstNonBlank(item.getClickUrl(), payload.get("url"), "/game"));
        String iconUrl = branding.applicationIcon(firstNonBlank(item.getIconUrl(), payload.get("icon")));
        String badgeUrl = branding.applicationIcon(firstNonBlank(item.getBadgeUrl(), payload.get("badge")));
        payload.remove("icon");
        payload.remove("badge");
        putIfText(payload, "icon", iconUrl);
        putIfText(payload, "badge", badgeUrl);
        putIfText(payload, "tag", firstNonBlank(item.getTag(), payload.get("tag"), item.getNotificationType()));
        return payload;
    }

    private WebPush webpushConfig(NotificationOutboxItem item, Map<String, String> payload) {
        return new WebPush(firstNonBlank(item.getTitle(), payload.get("title")),
                firstNonBlank(item.getBody(), payload.get("body"), "You have a new notification."),
                payload.get("icon"), payload.get("badge"), payload.get("tag"), Instant.now().toEpochMilli(),
                urgencyFor(item.getNotificationType()), payload.get("url"));
    }

    private ResolvedDeploymentBranding snapshottedBranding(
            Map<String, String> payload) {
        String revisionValue = firstNonBlank(
                payload == null
                        ? null
                        : payload.get("productBrandingRevision"),
                payload == null
                        ? null
                        : payload.get("product_branding_revision"));
        long revision = 0L;
        try {
            revision = Math.max(
                    0L,
                    Long.parseLong(firstNonBlank(revisionValue, "0")));
        } catch (NumberFormatException ignored) {
            revision = 0L;
        }
        return new ResolvedDeploymentBranding(
                firstNonBlank(
                        payload == null ? null : payload.get("productName"),
                        payload == null ? null : payload.get("product_name")),
                firstNonBlank(
                        payload == null
                                ? null
                                : payload.get("productLogoUrl"),
                        payload == null
                                ? null
                                : payload.get("product_logo_url")),
                revision);
    }

    private List<TokenRegistration> tokenRegistrations(NotificationOutboxItem item, List<Recipient> users) {
        LinkedHashMap<String, TokenRegistration> byToken = new LinkedHashMap<>();
        for (Recipient user : users == null ? List.<Recipient>of() : users) {
            if (!user.hasDeliveryLocation()) {
                continue;
            }
            String userId = normalizeId(user.userId());
            if (!StringUtils.hasText(userId) || user.devices() == null) {
                continue;
            }
            for (Device device : user.devices()) {
                if (device == null || !device.enabled()) {
                    continue;
                }
                String token = safeText(device.token());
                if (item.getFirebaseDeliveryRequested() != null) {
                    Map<String, String> planned = item.getFirebaseDevices() == null ? Map.of()
                            : item.getFirebaseDevices().getOrDefault(userId, Map.of());
                    // Resolve only the saved registration; a revoke/rotation cannot retarget old work.
                    if (!NotificationOutboxService.sha256(token).equals(planned.get(safeText(device.deviceId())))) continue;
                }
                if (StringUtils.hasText(token)) {
                    byToken.putIfAbsent(token, new TokenRegistration(userId, token));
                }
            }
        }
        return new ArrayList<>(byToken.values());
    }

    private void removeInvalidTokens(List<Recipient> recipients, Set<String> invalidTokens) {
        if (recipients == null || recipients.isEmpty() || invalidTokens == null || invalidTokens.isEmpty()) return;
        var cleanup = accounts.removeInvalidPushTokens(recipients.stream().map(Recipient::userId).toList(), invalidTokens);
        if (cleanup.changedUsers() > 0) {
            adminTelemetryService.record("notification.tokens.removed", "notifications", "", "system",
                    cleanup.removedTokens(), Map.of("users", Integer.toString(cleanup.changedUsers())));
        }
    }

    private boolean isInvalidToken(String code) {
        if (code == null) {
            return false;
        }
        return "UNREGISTERED".equals(code) || "INVALID_ARGUMENT".equals(code);
    }

    private void markSent(NotificationOutboxItem item, DeliveryResult result, String nowIso) {
        Update update = new Update()
                .set("status", NotificationOutboxItem.STATUS_SENT)
                .set("pendingCounterTracked", false)
                .set("sentDate", nowIso)
                .set("updatedDate", nowIso)
                .set("tokenSuccessCount", result.successCount())
                .set("tokenFailureCount", result.failureCount())
                .unset("lockedUntil")
                .unset("lastError");
        UpdateResult updateResult = mongoTemplate.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(item.getId()),
                        Criteria.where("status").is(NotificationOutboxItem.STATUS_RUNNING))),
                update,
                NotificationOutboxItem.class);
        if (updateResult.getModifiedCount() > 0 && Boolean.TRUE.equals(item.getPendingCounterTracked())) {
            refreshPendingCount();
        }
    }

    private void markFailed(NotificationOutboxItem item, Exception exception, Instant now) {
        if (item == null) {
            LOGGER.warn("Notification outbox delivery failed before item could be loaded.", exception);
            return;
        }
        int retryCount = Math.max(0, item.getRetryCount() == null ? 0 : item.getRetryCount()) + 1;
        boolean exhausted = retryCount >= effectiveMaxRetries();
        String nowIso = now.toString();
        String nextRetryAt = now.plus(retryDelay(retryCount)).toString();
        Update update = new Update()
                .set("status", exhausted ? NotificationOutboxItem.STATUS_FAILED : NotificationOutboxItem.STATUS_PENDING)
                .set("pendingCounterTracked", exhausted ? false : Boolean.TRUE.equals(item.getPendingCounterTracked()))
                .set("retryCount", retryCount)
                .set("nextRetryAt", exhausted ? null : nextRetryAt)
                .set("lastError", truncateError(exception))
                .set("updatedDate", nowIso)
                .unset("lockedUntil");
        UpdateResult updateResult = mongoTemplate.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(item.getId()),
                        Criteria.where("status").is(NotificationOutboxItem.STATUS_RUNNING))),
                update,
                NotificationOutboxItem.class);
        if (exhausted && updateResult.getModifiedCount() > 0 && Boolean.TRUE.equals(item.getPendingCounterTracked())) {
            refreshPendingCount();
        }
        LOGGER.warn("Notification outbox item {} delivery failed.", item.getId(), exception);
    }

    private void refreshPendingCount() {
        var registry = notificationRuleRegistry();
        if (registry == null) return;
        try { registry.refreshOutboxPendingCount(); }
        catch (RuntimeException unavailable) {
            LOGGER.warn("Notification queue metric remains pending ({})", unavailable.getClass().getSimpleName());
        }
    }

    private NotificationRuleRegistryService notificationRuleRegistry() {
        return notificationRuleRegistryServiceProvider == null
                ? null
                : notificationRuleRegistryServiceProvider.getIfAvailable();
    }

    private Duration retryDelay(int retryCount) {
        long multiplier = 1L << Math.min(8, Math.max(0, retryCount - 1));
        return effectiveRetryBaseDelay().multipliedBy(multiplier);
    }

    private boolean isInsideDeliveryWindow() {
        int startHour = effectiveDeliveryWindowStartHour();
        int endHour = effectiveDeliveryWindowEndHour();
        if (startHour == endHour) {
            return true;
        }
        int currentHour = LocalTime.now(ZoneId.systemDefault()).getHour();
        if (startHour < endHour) {
            return currentHour >= startHour && currentHour < endHour;
        }
        return currentHour >= startHour || currentHour < endHour;
    }

    private int effectiveBatchSize() {
        return batchSize;
    }

    private int effectiveMaxWorkers() {
        return platformParametersService == null
                ? 1
                : platformParametersService.notificationMaxWorkers(1);
    }

    private int effectiveMaxRetries() {
        return platformParametersService == null
                ? maxRetries
                : platformParametersService.notificationMaxRetries(maxRetries);
    }

    private Duration effectiveRetryBaseDelay() {
        return platformParametersService == null
                ? retryBaseDelay
                : platformParametersService.notificationInitialBackoff(retryBaseDelay);
    }

    private int effectiveDeliveryWindowStartHour() {
        return platformParametersService == null
                ? deliveryWindowStartHour
                : platformParametersService.notificationWindowStartHour(deliveryWindowStartHour);
    }

    private int effectiveDeliveryWindowEndHour() {
        return platformParametersService == null
                ? deliveryWindowEndHour
                : platformParametersService.notificationWindowEndHour(deliveryWindowEndHour);
    }

    private int clampHour(int value, int fallback) {
        if (value < 0 || value > 24) {
            return fallback;
        }
        return value;
    }

    private String urgencyFor(String type) {
        String normalized = safeText(type);
        return normalized.contains("chat") || normalized.contains("invite") || normalized.contains("asset-review")
                ? "high"
                : "normal";
    }

    private List<String> compactIds(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::normalizeId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private void putIfText(Map<String, String> payload, String key, String value) {
        String normalized = safeText(value);
        if (StringUtils.hasText(key) && StringUtils.hasText(normalized)) {
            payload.put(key, normalized);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values == null ? new String[0] : values) {
            String normalized = safeText(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    private String truncateError(Exception exception) {
        String message = exception == null ? "" : safeText(exception.getMessage());
        if (message.length() <= 1000) {
            return message;
        }
        return message.substring(0, 1000);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record TokenRegistration(String userId, String token) {
    }

    private record DeliveryResult(int successCount, int failureCount, Set<String> invalidTokens) {
    }
}
