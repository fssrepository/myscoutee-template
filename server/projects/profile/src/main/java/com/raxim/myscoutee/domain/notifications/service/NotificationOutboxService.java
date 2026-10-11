package com.raxim.myscoutee.domain.notifications.service;

import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import com.raxim.myscoutee.domain.platform.api.AuthenticationMode;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications.Recipient;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications.Device;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.chat.api.ChatPresence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem;
import com.raxim.myscoutee.domain.notifications.repository.NotificationOutboxRepository;
import com.raxim.myscoutee.domain.platform.api.NotificationPolicy;
import com.raxim.myscoutee.common.redis.RedisStreamRetentionService;
import com.raxim.myscoutee.domain.platform.api.DeploymentBranding;
import com.raxim.myscoutee.domain.platform.api.DeploymentBranding.ResolvedDeploymentBranding;

@Service
public class NotificationOutboxService implements com.raxim.myscoutee.domain.notifications.api.NotificationDelivery {
    public static final String WAKEUP_STREAM_KEY = "notification:outbox:wakeups";
    public static String wakeupStreamKey() {
        return WAKEUP_STREAM_KEY + ":" + com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext.currentMode().name() + ":" + JobGroupContext.key();
    }
    private static final Duration WAKEUP_STREAM_RETENTION = Duration.ofDays(30);
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationOutboxService.class);
    private static final String DEFAULT_CLICK_URL = "/game";
    private static final String DELIVERY_STRATEGY_DIRECT = "direct";
    private static final String DELIVERY_STRATEGY_MULTICAST = "multicast";
    private static final String DELIVERY_STRATEGY_TOPIC = "topic";
    private static final int DEFAULT_MULTICAST_THRESHOLD = 3;
    private static final int DEFAULT_TOPIC_THRESHOLD = 1000;
    private static final int DEFAULT_COLLAPSE_WINDOW_SECONDS = 300;


    @org.springframework.beans.factory.annotation.Autowired
    private ObjectProvider<com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents> integrationWebhooks;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationPolicy platformParametersService;
    private final RedisStreamRetentionService redisStreamRetentionService;
    private final ObjectProvider<UserNotificationService> userNotificationServiceProvider;
    private ObjectProvider<NotificationRuleRegistryService> notificationRuleRegistryServiceProvider;
    private DeploymentBranding deploymentBrandingResolver;
    private MongoTemplate mongoTemplate;
    private AuthenticationMode configProperties;
    private AccountNotifications accounts;
    private ChatPresence chatWebSocketSessionRegistry;

    @Autowired
    void configureDelivery(AuthenticationMode configProperties, AccountNotifications accounts,
            ChatPresence chatWebSocketSessionRegistry) {
        this.configProperties = configProperties;
        this.accounts = accounts;
        this.chatWebSocketSessionRegistry = chatWebSocketSessionRegistry;
    }

    @Autowired
    public NotificationOutboxService(
            NotificationOutboxRepository notificationOutboxRepository,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            NotificationPolicy platformParametersService,
            RedisStreamRetentionService redisStreamRetentionService,
            ObjectProvider<UserNotificationService> userNotificationServiceProvider) {
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.stringRedisTemplate = stringRedisTemplateProvider == null ? null : stringRedisTemplateProvider.getIfAvailable();
        this.platformParametersService = platformParametersService;
        this.redisStreamRetentionService = redisStreamRetentionService;
        this.userNotificationServiceProvider = userNotificationServiceProvider;
    }

    public NotificationOutboxService(
            NotificationOutboxRepository notificationOutboxRepository,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            NotificationPolicy platformParametersService,
            RedisStreamRetentionService redisStreamRetentionService) {
        this(
                notificationOutboxRepository,
                stringRedisTemplateProvider,
                platformParametersService,
                redisStreamRetentionService,
                null);
    }

    NotificationOutboxService(
            NotificationOutboxRepository notificationOutboxRepository,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
            NotificationPolicy platformParametersService) {
        this(notificationOutboxRepository, stringRedisTemplateProvider, platformParametersService, null);
    }

    NotificationOutboxService(NotificationOutboxRepository notificationOutboxRepository) {
        this(notificationOutboxRepository, null, null, null);
    }

    @Autowired(required = false)
    void setNotificationRuleRegistryServiceProvider(
            ObjectProvider<NotificationRuleRegistryService> notificationRuleRegistryServiceProvider) {
        this.notificationRuleRegistryServiceProvider = notificationRuleRegistryServiceProvider;
    }

    @Autowired(required = false)
    void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Autowired(required = false)
    void setDeploymentBrandingResolver(
            DeploymentBranding deploymentBrandingResolver) {
        this.deploymentBrandingResolver = deploymentBrandingResolver;
    }

    public Optional<NotificationOutboxItem> enqueue(NotificationIntent intent) {
        return enqueueByGroup(intent, null);
    }

    private Optional<NotificationOutboxItem> enqueueByGroup(NotificationIntent intent, String operationId) {
        if (intent == null) return Optional.empty();
        return operationId == null ? enqueueInGroup(intent) : enqueueOnceInGroup(operationId, intent);
    }

    private Optional<NotificationOutboxItem> enqueueInGroup(NotificationIntent intent) {
        if (intent == null) {
            return Optional.empty();
        }
        String type = safeText(intent.type());
        ResolvedDeploymentBranding branding = deploymentBranding(intent);
        Map<String, String> payload = normalizedPayload(intent, branding);
        RecipientResolution resolution = accountRecipients(intent.recipientUserIds(), payload);
        List<String> recipientUserIds = resolution.ids();
        String topicName = resolveStableTopicName(intent, payload, recipientUserIds.size());
        String deliveryStrategy = StringUtils.hasText(topicName)
                ? DELIVERY_STRATEGY_TOPIC
                : knownUserDeliveryStrategy(payload, recipientUserIds.size());
        if (!StringUtils.hasText(type) || (recipientUserIds.isEmpty() && !StringUtils.hasText(topicName))) {
            return Optional.empty();
        }

        String nowIso = Instant.now().toString();
        String payloadHash = sha256(canonicalPayload(payload));
        String entityType = safeText(intent.entityType());
        String entityId = safeText(intent.entityId());
        String recipientScope = firstNonBlank(intent.recipientScope(), "users");
        String collapseKey = String.join(":",
                type,
                StringUtils.hasText(entityType) ? entityType : "none",
                StringUtils.hasText(entityId) ? entityId : "none",
                recipientScope,
                payloadHash);

        if (JobGroupContext.groupId() != null) collapseKey = JobGroupContext.key() + ":" + collapseKey;
        NotificationOutboxItem existing = notificationOutboxRepository
                .findFirstByCollapseKeyAndStatusInAndCreatedDateGreaterThanEqualOrderByCreatedDateAsc(
                        collapseKey,
                        List.of(NotificationOutboxItem.STATUS_PENDING),
                        collapseWindowStartIso(nowIso))
                .orElse(null);
        TrackedOutboxSave trackedSave = existing != null && mongoTemplate != null
                ? mergeExistingItemAtomic(existing, recipientUserIds, payload, nowIso, intent.createdUser(), resolution.profiles())
                : null;
        NotificationOutboxItem saved;
        boolean newlyTracked;
        if (trackedSave != null) {
            saved = trackedSave.item();
            newlyTracked = trackedSave.newlyTracked();
        } else {
            NotificationOutboxItem item = existing == null || mongoTemplate != null
                    ? newItem(intent, recipientUserIds, payload, payloadHash, collapseKey, deliveryStrategy, topicName, nowIso, resolution.profiles())
                    : mergeRecipients(existing, recipientUserIds, payload, nowIso, intent.createdUser(), resolution.profiles());
            newlyTracked = !Boolean.TRUE.equals(item.getPendingCounterTracked());
            item.setPendingCounterTracked(true);
            saved = notificationOutboxRepository.save(item);
        }
        refreshPendingCount();
        materializeInbox(saved);
        return Optional.of(saved);
    }

    /** Durable operation identity; retries do not create another inbox/provider delivery. */
    public Optional<NotificationOutboxItem> enqueueOnce(String operationId, NotificationIntent intent) {
        return enqueueByGroup(intent, operationId);
    }

    private Optional<NotificationOutboxItem> enqueueOnceInGroup(String operationId, NotificationIntent intent) {
        String id = "operation:" + sha256(JobGroupContext.groupId() == null ? operationId : JobGroupContext.key() + ":" + operationId);
        NotificationOutboxItem saved = notificationOutboxRepository.findById(id).orElse(null);
        if (saved == null) {
            Map<String, String> payload = normalizedPayload(intent, deploymentBranding(intent));
            RecipientResolution resolution = accountRecipients(intent.recipientUserIds(), payload);
            List<String> recipients = resolution.ids();
            if (recipients.isEmpty()) return Optional.empty();
            String topic = resolveStableTopicName(intent, payload, recipients.size());
            NotificationOutboxItem item = newItem(intent, recipients, payload,
                    sha256(canonicalPayload(payload)), id,
                    StringUtils.hasText(topic) ? DELIVERY_STRATEGY_TOPIC : knownUserDeliveryStrategy(payload, recipients.size()),
                    topic, Instant.now().toString(), resolution.profiles());
            item.setId(id);
            item.setSyncKey(id);
            item.setPendingCounterTracked(true);
            try {
                saved = notificationOutboxRepository.insert(item);
            } catch (org.springframework.dao.DuplicateKeyException concurrentInsert) {
                saved = notificationOutboxRepository.findById(id).orElseThrow();
            }
        }
        refreshPendingCount();
        materializeInbox(saved);
        return Optional.of(saved);
    }

    private TrackedOutboxSave mergeExistingItemAtomic(
            NotificationOutboxItem existing,
            List<String> recipientUserIds,
            Map<String, String> payload,
            String nowIso,
            String actorUserId, Map<String, Recipient> recipientProfiles) {
        if (existing == null || mongoTemplate == null || !StringUtils.hasText(safeText(existing.getId()))) {
            return null;
        }
        AggregationUpdate update = AggregationUpdate.update()
                .set("pendingCounterTracked").toValue(true)
                .set("nextRetryAt").toValue(firstNonBlank(existing.getNextRetryAt(), nowIso))
                .set("updatedDate").toValue(nowIso)
                .set("updatedUser").toValue((AggregationExpression) context ->
                        new Document("$literal", safeNullableText(actorUserId)))
                .set("recipientUserIds").toValue((AggregationExpression) context -> new Document(
                        "$setUnion", List.of(new Document("$ifNull", List.of("$recipientUserIds", List.of())),
                                new Document("$literal", recipientUserIds))));
        if (existing.getFirebaseDeliveryRequested() != null) {
            Map<String, Map<String, String>> additional = snapshotFirebaseDevices(existing,
                    recipientUserIds.stream().filter(id -> !existing.getRecipientUserIds().contains(id)).toList(), recipientProfiles);
            // Existing per-user decisions win, including an explicitly empty device map.
            update.set("firebaseDevices").toValue((AggregationExpression) context -> new Document(
                    "$mergeObjects", List.of(new Document("$literal", additional),
                            new Document("$ifNull", List.of("$firebaseDevices", new Document())))));
        }
        NotificationOutboxItem previous = mongoTemplate.findAndModify(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(existing.getId()),
                        Criteria.where("status").is(NotificationOutboxItem.STATUS_PENDING))),
                update,
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(false),
                NotificationOutboxItem.class);
        if (previous == null) {
            return null;
        }
        NotificationOutboxItem saved = notificationOutboxRepository.findById(existing.getId())
                .orElseGet(() -> mergeRecipients(existing, recipientUserIds, payload, nowIso, actorUserId, recipientProfiles));
        if (!DELIVERY_STRATEGY_TOPIC.equals(safeText(saved.getDeliveryStrategy()))) {
            String strategy = knownUserDeliveryStrategy(
                    firstNonEmptyMap(saved.getPayload(), payload),
                    compactIds(saved.getRecipientUserIds()).size());
            mongoTemplate.updateFirst(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("_id").is(saved.getId()),
                            Criteria.where("status").is(NotificationOutboxItem.STATUS_PENDING))),
                    new Update().set("deliveryStrategy", strategy),
                    NotificationOutboxItem.class);
            saved.setDeliveryStrategy(strategy);
        }
        saved.setPendingCounterTracked(true);
        return new TrackedOutboxSave(saved, !Boolean.TRUE.equals(previous.getPendingCounterTracked()));
    }

    private record TrackedOutboxSave(NotificationOutboxItem item, boolean newlyTracked) {}

    private void refreshPendingCount() {
        var registry = notificationRuleRegistryServiceProvider == null ? null : notificationRuleRegistryServiceProvider.getIfAvailable();
        if (registry == null) return;
        try { registry.refreshOutboxPendingCount(); }
        catch (RuntimeException unavailable) {
            // The durable outbox is the work receipt. Its worker refreshes the metric later.
            LOGGER.warn("Notification queue metric remains pending ({})", unavailable.getClass().getSimpleName());
        }
    }

    private void materializeInbox(NotificationOutboxItem item) {
        if (userNotificationServiceProvider == null || item == null) {
            return;
        }
        UserNotificationService userNotificationService = userNotificationServiceProvider.getIfAvailable();
        if (userNotificationService != null) {
            userNotificationService.materialize(item);
            if (integrationWebhooks != null && ("app-admin".equals(item.getEntityType()) || "operator".equals(item.getEntityType()))) {
                integrationWebhooks.getObject().enqueueRoleAttention(new com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents.RoleAttention(
                        item.getId(), item.getEntityType(), item.getNotificationType(), item.getRecipientUserIds()));
            }
        }
    }

    private NotificationOutboxItem newItem(
            NotificationIntent intent,
            List<String> recipientUserIds,
            Map<String, String> payload,
            String payloadHash,
            String collapseKey,
            String deliveryStrategy,
            String topicName,
            String nowIso, Map<String, Recipient> recipientProfiles) {
        NotificationOutboxItem item = new NotificationOutboxItem();
        item.setId(new ObjectId().toHexString());
        item.setBaseGroupId(JobGroupContext.groupId());
        item.setSyncKey(UUID.randomUUID().toString());
        item.setNotificationType(safeText(intent.type()));
        item.setEntityType(safeNullableText(intent.entityType()));
        item.setEntityId(safeNullableText(intent.entityId()));
        item.setRecipientScope(firstNonBlank(intent.recipientScope(), "users"));
        item.setRecipientUserIds(recipientUserIds);
        item.setDeliveryStrategy(deliveryStrategy);
        item.setFirebaseDeliveryRequested(configProperties != null && configProperties.isFirebaseEnabled());
        item.setFirebaseDevices(snapshotFirebaseDevices(item, recipientUserIds, recipientProfiles));
        item.setTopicName(safeNullableText(topicName));
        item.setTitle(firstNonBlank(
                payload.get("title"),
                payload.get("productName")));
        item.setBody(firstNonBlank(payload.get("body"), intent.body()));
        item.setClickUrl(firstNonBlank(payload.get("url"), DEFAULT_CLICK_URL));
        item.setIconUrl(firstNonBlank(
                payload.get("icon"),
                payload.get("productLogoUrl")));
        item.setBadgeUrl(firstNonBlank(
                payload.get("badge"),
                payload.get("productLogoUrl")));
        item.setTag(firstNonBlank(intent.tag(), item.getNotificationType()));
        item.setPayload(payload);
        item.setPayloadHash(payloadHash);
        item.setCollapseKey(collapseKey);
        item.setStatus(NotificationOutboxItem.STATUS_PENDING);
        item.setRetryCount(0);
        item.setNextRetryAt(nowIso);
        item.setTokenSuccessCount(0);
        item.setTokenFailureCount(0);
        item.setCreatedDate(nowIso);
        item.setCreatedUser(safeNullableText(intent.createdUser()));
        item.setUpdatedDate(nowIso);
        item.setUpdatedUser(safeNullableText(intent.createdUser()));
        return item;
    }

    private NotificationOutboxItem mergeRecipients(
            NotificationOutboxItem item,
            List<String> recipientUserIds,
            Map<String, String> payload,
            String nowIso,
            String actorUserId, Map<String, Recipient> recipientProfiles) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(compactIds(item.getRecipientUserIds()));
        if (item.getFirebaseDeliveryRequested() != null) {
            Map<String, Map<String, String>> devices = new LinkedHashMap<>(
                    item.getFirebaseDevices() == null ? Map.of() : item.getFirebaseDevices());
            snapshotFirebaseDevices(item, recipientUserIds.stream().filter(id -> !merged.contains(id)).toList(), recipientProfiles)
                    .forEach(devices::putIfAbsent);
            item.setFirebaseDevices(devices);
        }
        merged.addAll(recipientUserIds);
        item.setRecipientUserIds(List.copyOf(merged));
        if (!DELIVERY_STRATEGY_TOPIC.equals(safeText(item.getDeliveryStrategy()))) {
            item.setDeliveryStrategy(knownUserDeliveryStrategy(firstNonEmptyMap(item.getPayload(), payload), merged.size()));
        }
        item.setNextRetryAt(firstNonBlank(item.getNextRetryAt(), nowIso));
        item.setUpdatedDate(nowIso);
        item.setUpdatedUser(safeNullableText(actorUserId));
        return item;
    }

    private Map<String, Map<String, String>> snapshotFirebaseDevices(
            NotificationOutboxItem item, List<String> recipients, Map<String, Recipient> profiles) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        recipients.forEach(id -> result.put(id, new LinkedHashMap<>()));
        if (!Boolean.TRUE.equals(item.getFirebaseDeliveryRequested()) || recipients.isEmpty()
                || DELIVERY_STRATEGY_TOPIC.equals(item.getDeliveryStrategy()) || accounts == null) {
            return result;
        }
        List<String> missing = recipients.stream().filter(id -> !profiles.containsKey(id)).toList();
        if (!missing.isEmpty()) accounts.recipients(missing).forEach(profile -> profiles.put(profile.userId(), profile));
        for (String recipientId : recipients) {
            Recipient user = profiles.get(recipientId);
            if (user == null || !user.hasDeliveryLocation()) continue;
            Map<String, String> devices = result.get(safeText(user.userId()));
            if (devices == null || user.devices() == null) continue;
            if ("chat-message".equals(item.getNotificationType()) && chatWebSocketSessionRegistry != null
                    && chatWebSocketSessionRegistry.isUserConnected(item.getEntityId(), user.userId())) continue;
            for (Device device : user.devices()) {
                if (device != null && device.enabled()
                        && StringUtils.hasText(device.deviceId()) && StringUtils.hasText(device.token())) {
                    devices.put(safeText(device.deviceId()), sha256(safeText(device.token())));
                }
            }
        }
        return result;
    }

    private String resolveStableTopicName(NotificationIntent intent, Map<String, String> payload, int recipientCount) {
        String recipientScope = safeText(intent == null ? null : intent.recipientScope());
        String topicName = safeText(payload == null ? null : payload.get("topicName"));
        if (recipientScope.startsWith("topic:")) {
            topicName = recipientScope.substring("topic:".length()).trim();
        }
        if (!StringUtils.hasText(topicName)) {
            return null;
        }
        boolean stableTopic = payload != null && "true".equalsIgnoreCase(safeText(payload.get("stableTopic")))
                || recipientScope.startsWith("topic:")
                || topicName.startsWith("platform-")
                || topicName.startsWith("admin-")
                || topicName.startsWith("global-");
        if (!stableTopic) {
            return null;
        }
        int threshold = readInt(payload == null ? null : payload.get("topicThreshold"), topicThreshold());
        return recipientCount == 0 || recipientCount >= Math.max(1, threshold) ? topicName : null;
    }

    private String knownUserDeliveryStrategy(Map<String, String> payload, int recipientCount) {
        int threshold = readInt(payload == null ? null : payload.get("multicastThreshold"), multicastThreshold());
        return recipientCount >= Math.max(1, threshold) ? DELIVERY_STRATEGY_MULTICAST : DELIVERY_STRATEGY_DIRECT;
    }

    private String collapseWindowStartIso(String nowIso) {
        int windowSeconds = platformParametersService == null
                ? DEFAULT_COLLAPSE_WINDOW_SECONDS
                : platformParametersService.notificationCollapseWindowSeconds(DEFAULT_COLLAPSE_WINDOW_SECONDS);
        if (windowSeconds <= 0) {
            return nowIso;
        }
        try {
            return Instant.parse(nowIso).minusSeconds(windowSeconds).toString();
        } catch (Exception ignored) {
            return Instant.now().minusSeconds(windowSeconds).toString();
        }
    }

    private int topicThreshold() {
        return platformParametersService == null
                ? DEFAULT_TOPIC_THRESHOLD
                : platformParametersService.notificationTopicThreshold(DEFAULT_TOPIC_THRESHOLD);
    }

    private int multicastThreshold() {
        return platformParametersService == null
                ? DEFAULT_MULTICAST_THRESHOLD
                : platformParametersService.notificationMulticastThreshold(DEFAULT_MULTICAST_THRESHOLD);
    }

    private Map<String, String> firstNonEmptyMap(Map<String, String> first, Map<String, String> second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null ? Map.of() : second;
    }


    private RecipientResolution accountRecipients(java.util.Collection<String> ids, Map<String, String> payload) {
        var recipients = compactIds(ids);
        var snapshots = new LinkedHashMap<String, Recipient>();
        if (accounts == null || recipients.isEmpty()) return new RecipientResolution(recipients, snapshots);
        recipients.forEach(id -> snapshots.put(id, null));
        var profiles = accounts.recipients(recipients);
        var accountIds = new java.util.HashMap<String, String>();
        var workspaces = new java.util.HashSet<String>();
        for (var profile : profiles) {
            snapshots.put(profile.userId(), profile);
            if (StringUtils.hasText(profile.accountUserId())) accountIds.put(profile.userId(), profile.accountUserId());
            if (StringUtils.hasText(profile.workspaceGroupId())) workspaces.add(profile.workspaceGroupId());
        }
        if (workspaces.size() == 1) {
            String groupId = workspaces.iterator().next();
            payload.put("workspaceGroupId", groupId);
            String url = payload.get("url");
            if (StringUtils.hasText(url)) payload.put("url", org.springframework.web.util.UriComponentsBuilder.fromUriString(url)
                    .replaceQueryParam("workspaceGroupId", groupId).build().toUriString());
        }
        return new RecipientResolution(recipients.stream().map(id -> accountIds.getOrDefault(id, id)).distinct().toList(), snapshots);
    }

    private record RecipientResolution(List<String> ids, Map<String, Recipient> profiles) {}

    private Map<String, String> normalizedPayload(
            NotificationIntent intent,
            ResolvedDeploymentBranding branding) {
        LinkedHashMap<String, String> payload = new LinkedHashMap<>();
        if (intent.payload() != null) {
            intent.payload().entrySet().stream()
                    .filter(entry -> StringUtils.hasText(safeText(entry.getKey())))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> payload.put(safeText(entry.getKey()), safeText(entry.getValue())));
        }
        putIfText(payload, "type", intent.type());
        putIfText(payload, "entityType", intent.entityType());
        putIfText(payload, "entityId", intent.entityId());
        putIfText(payload, "recipientScope", intent.recipientScope());
        payload.putIfAbsent(
                "notification_title_key",
                notificationKey(intent.type(), "title"));
        payload.putIfAbsent(
                "notification_message_key",
                notificationKey(intent.type(), "message"));
        putIfText(payload, "productName", branding.productName());
        putIfText(payload, "productLogoUrl", branding.logoUrl());
        putIfText(
                payload,
                "productBrandingRevision",
                Long.toString(branding.revision()));
        putIfText(payload, "title", firstNonBlank(
                intent.title(),
                payload.get("title"),
                branding.productName()));
        putIfText(payload, "body", firstNonBlank(intent.body(), payload.get("body")));
        putIfText(payload, "url", firstNonBlank(intent.clickUrl(), payload.get("url"), DEFAULT_CLICK_URL));
        putIfText(payload, "icon", branding.applicationIcon(firstNonBlank(
                intent.iconUrl(),
                payload.get("icon"))));
        putIfText(payload, "badge", branding.applicationIcon(firstNonBlank(
                intent.badgeUrl(),
                payload.get("badge"))));
        putIfText(payload, "tag", firstNonBlank(intent.tag(), payload.get("tag"), intent.type()));
        return payload;
    }

    private String notificationKey(String type, String field) {
        String normalizedType = safeText(type)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return "notification.kind." + normalizedType + "." + field;
    }

    private ResolvedDeploymentBranding deploymentBranding(
            NotificationIntent intent) {
        Map<String, String> payload =
                intent == null || intent.payload() == null
                        ? Map.of()
                        : intent.payload();
        String productName = firstNonBlank(
                payload.get("productName"),
                payload.get("product_name"));
        String logoUrl = firstNonBlank(
                payload.get("productLogoUrl"),
                payload.get("product_logo_url"));
        Long revision = parseNonNegativeLong(firstNonBlank(
                payload.get("productBrandingRevision"),
                payload.get("product_branding_revision")));
        if (StringUtils.hasText(productName)
                && StringUtils.hasText(logoUrl)
                && revision != null) {
            return new ResolvedDeploymentBranding(
                    productName,
                    logoUrl,
                    revision);
        }
        return deploymentBrandingResolver == null
                ? com.raxim.myscoutee.domain.platform.api.DeploymentBranding.fallback()
                : deploymentBrandingResolver.resolve();
    }

    private Long parseNonNegativeLong(String value) {
        try {
            long parsed = Long.parseLong(safeText(value));
            return parsed < 0L ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void putIfText(Map<String, String> payload, String key, String value) {
        String normalized = safeText(value);
        if (StringUtils.hasText(key) && StringUtils.hasText(normalized)) {
            payload.put(key, normalized);
        }
    }

    private List<String> compactIds(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(NotificationOutboxService::safeText)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String canonicalPayload(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        return payload.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> safeText(entry.getKey()) + "=" + safeText(entry.getValue()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(safeText(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : hashed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(safeText(value).hashCode());
        }
    }

    private int readInt(String value, int fallback) {
        try {
            return Integer.parseInt(safeText(value));
        } catch (Exception ignored) {
            return fallback;
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

    private String safeNullableText(String value) {
        String normalized = safeText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

}
