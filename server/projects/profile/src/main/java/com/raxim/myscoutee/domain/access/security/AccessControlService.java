package com.raxim.myscoutee.domain.access.security;

import com.raxim.myscoutee.domain.access.api.AccessGrantEffect;
import com.raxim.myscoutee.domain.access.api.AccessPolicyKeys;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import com.raxim.myscoutee.domain.access.document.AccessGrant;
import com.raxim.myscoutee.domain.access.document.AccessPolicyDefinition;
import com.raxim.myscoutee.domain.access.repository.AccessGrantsRepository;
import com.raxim.myscoutee.domain.access.repository.AccessPolicyDefinitionsRepository;

import jakarta.annotation.PostConstruct;

@Service("accessControl")
public class AccessControlService implements AccessPolicyData, com.raxim.myscoutee.domain.access.api.AccessPermissions {
    private static final String EFFECT_ALLOW = "allow";
    private static final String EFFECT_DENY = "deny";

    private final AccessGrantsRepository accessGrantsRepository;
    private final AccessPolicyDefinitionsRepository accessPolicyDefinitionsRepository;
    private final RuntimeConfiguration configProperties;
    private final AccessPolicyConditionRegistry conditionRegistry;
    private final ChatPolicyFacts chatPolicyFacts;
    private static final class AccessCache {
        final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
        volatile long loadedRevision = -1;
        volatile Instant nextGrantExpiry;
        volatile Map<GrantKey, AccessGrantEffect> grants = Map.of();
        volatile Map<String, AccessPolicy> policies = Map.of();
    }
    private final Map<MongoSessionMode, AccessCache> caches = new java.util.EnumMap<>(MongoSessionMode.class);

    private AccessCache currentCache() {
        AccessCache cache = caches.get(MongoSessionModeContext.currentMode());
        synchronized (cache) {
            while (cache.loadedRevision != cache.revision.get()) refreshAccessControlCache();
            if (cache.nextGrantExpiry != null && !cache.nextGrantExpiry.isAfter(Instant.now())) refreshAccessGrantCache();
        }
        return cache;
    }

    public AccessControlService(
            AccessGrantsRepository accessGrantsRepository,
            AccessPolicyDefinitionsRepository accessPolicyDefinitionsRepository,
            RuntimeConfiguration configProperties,
            AccessPolicyConditionRegistry conditionRegistry,
            ChatPolicyFacts chatPolicyFacts) {
        this.accessGrantsRepository = accessGrantsRepository;
        this.accessPolicyDefinitionsRepository = accessPolicyDefinitionsRepository;
        this.configProperties = configProperties;
        this.conditionRegistry = conditionRegistry;
        this.chatPolicyFacts = chatPolicyFacts;
        for (MongoSessionMode mode : MongoSessionMode.values()) caches.put(mode, new AccessCache());
    }

    @PostConstruct
    public void loadAccessControlCacheAtStartup() {
        refreshAccessControlCache();
    }

    @org.springframework.beans.factory.annotation.Autowired
    void watchAccessPolicy(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes,
            com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner databases) {
        if (!databases.hasDistinctDemoDatabase()) caches.put(MongoSessionMode.DEMO, caches.get(MongoSessionMode.REAL));
        Runnable invalidate = () -> caches.get(MongoSessionModeContext.currentMode()).revision.incrementAndGet();
        changes.watch("access-policy", java.util.Set.of("accessPolicyDefinitions", "accessGrants"), java.util.Set.of(), () -> {
            refreshAccessControlCache();
            return caches.get(MongoSessionModeContext.currentMode()).nextGrantExpiry;
        }, invalidate, invalidate);
    }

    public void refreshAccessControlCache() {
        AccessCache cache = caches.get(MongoSessionModeContext.currentMode());
        synchronized (cache) {
            long revision = cache.revision.get();
            refreshAccessPolicyCache();
            refreshAccessGrantCache();
            cache.loadedRevision = revision;
        }
    }

    public void refreshAccessPolicyCache() {
        Map<String, AccessPolicy> nextCache = new LinkedHashMap<>();
        for (AccessPolicyDefinition definition : accessPolicyDefinitionsRepository.findAll()) {
            AccessPolicy policy = compilePolicy(definition);
            if (policy != null) {
                nextCache.put(policy.policyKey(), policy);
            }
        }
        caches.get(MongoSessionModeContext.currentMode()).policies = Map.copyOf(nextCache);
    }

    public void refreshAccessGrantCache() {
        Map<GrantKey, AccessGrantEffect> nextCache = new LinkedHashMap<>();
        Instant now = Instant.now();
        Instant earliestExpiry = null;
        for (AccessGrant grant : accessGrantsRepository.findAll()) {
            GrantKey key = grantKey(grant);
            AccessGrantEffect effect = grantEffect(grant);
            if (key == null || effect == null || isExpired(grant == null ? null : grant.getExpiresAtIso(), now)) {
                continue;
            }
            if (StringUtils.hasText(grant.getExpiresAtIso())) {
                Instant expiry = Instant.parse(grant.getExpiresAtIso().trim());
                if (earliestExpiry == null || expiry.isBefore(earliestExpiry)) earliestExpiry = expiry;
            }
            AccessGrantEffect current = nextCache.get(key);
            if (current == AccessGrantEffect.DENY) {
                continue;
            }
            nextCache.put(key, effect);
        }
        AccessCache cache = caches.get(MongoSessionModeContext.currentMode());
        cache.grants = Map.copyOf(nextCache);
        cache.nextGrantExpiry = earliestExpiry;
    }

    private AccessPolicy compilePolicy(AccessPolicyDefinition definition) {
        if (definition == null || !isPolicyEnabled(definition.getStatus())) {
            return null;
        }
        String policyKey = safeText(definition.getPolicyKey());
        String permission = safeText(definition.getPermission());
        String resourceType = safeText(definition.getResourceType());
        if (!StringUtils.hasText(policyKey)
                || !StringUtils.hasText(permission)
                || !StringUtils.hasText(resourceType)) {
            return null;
        }

        List<AccessPolicyCondition> required = compileConditions(definition.getRequired());
        List<AccessPolicyCondition> anyOf = compileConditions(definition.getAnyOf());
        if (required == null || anyOf == null) {
            return null;
        }
        return new AccessPolicy(policyKey, permission, resourceType, required, anyOf);
    }

    private List<AccessPolicyCondition> compileConditions(List<String> conditionNames) {
        if (conditionNames == null || conditionNames.isEmpty()) {
            return List.of();
        }
        List<AccessPolicyCondition> conditions = new ArrayList<>();
        for (String conditionName : conditionNames) {
            AccessPolicyCondition condition = conditionRegistry.named(conditionName);
            if (condition == null) {
                return null;
            }
            conditions.add(condition);
        }
        return conditions;
    }

    public boolean can(String policyKey, String actorUserId, String subjectUserId, String resourceId) {
        String normalizedPolicyKey = safeText(policyKey);
        String normalizedSubjectUserId = normalizeId(subjectUserId);
        String normalizedResourceId = normalizeId(resourceId);
        if (!StringUtils.hasText(normalizedPolicyKey)
                || !StringUtils.hasText(normalizedSubjectUserId)
                || !StringUtils.hasText(normalizedResourceId)) {
            return false;
        }

        AccessPolicy policy = currentCache().policies.get(normalizedPolicyKey);
        String actorId = normalizeId(actorUserId);
        if (policy != null && "chat".equals(policy.resourceType())) {
            String chatActorId = resolveChatActorUserId(normalizedResourceId, actorId);
            if (java.util.Objects.equals(actorId, normalizedSubjectUserId)) {
                normalizedSubjectUserId = chatActorId;
            }
            actorId = chatActorId;
        }
        return policy != null && policy.allows(
                actorId,
                normalizedSubjectUserId,
                normalizedResourceId,
                this);
    }

    public String resolveChatActorUserId(String chatId, String profileId) {
        return chatPolicyFacts.resolveActorUserId(chatId, profileId);
    }

    public boolean canCurrentActorCreateChatMessage(String userId, String chatId) {
        return canCurrentActorUsePolicy(AccessPolicyKeys.CHAT_MESSAGE_CREATE, userId, chatId);
    }

    public boolean canCurrentActorEditChatMessage(String userId, String chatId) {
        return canCurrentActorUsePolicy(AccessPolicyKeys.CHAT_MESSAGE_EDIT, userId, chatId);
    }

    public boolean canCurrentActorDeleteChatMessage(String userId, String chatId) {
        return canCurrentActorUsePolicy(AccessPolicyKeys.CHAT_MESSAGE_DELETE, userId, chatId);
    }

    public boolean canCurrentActorPinChatMessage(String userId, String chatId) {
        return canCurrentActorUsePolicy(AccessPolicyKeys.CHAT_MESSAGE_PIN, userId, chatId);
    }

    public boolean canCurrentActorManageChatReaction(String userId, String chatId) {
        return canCurrentActorUsePolicy(AccessPolicyKeys.CHAT_REACTION_MANAGE, userId, chatId);
    }

    public boolean canCurrentActorAddChatAttachment(String userId, String chatId) {
        return canCurrentActorUsePolicy(AccessPolicyKeys.CHAT_ATTACHMENT_ADD, userId, chatId);
    }

    public boolean canCurrentActorManageSupportChat(String userId, String chatId) {
        return canCurrentActorUsePolicy(AccessPolicyKeys.CHAT_SUPPORT_MANAGE, userId, chatId);
    }

    public boolean canCurrentActorEditProfile(String userId) {
        String normalizedUserId = normalizeId(userId);
        return canCurrentActorUsePolicy(AccessPolicyKeys.USER_PROFILE_EDIT, normalizedUserId, normalizedUserId);
    }

    public boolean canCurrentActorDeleteProfile(String userId) {
        String normalizedUserId = normalizeId(userId);
        return canCurrentActorUsePolicy(AccessPolicyKeys.USER_PROFILE_DELETE, normalizedUserId, normalizedUserId);
    }

    public boolean canCurrentActorUploadMedia(String ownerUserId) {
        String normalizedOwnerUserId = normalizeId(ownerUserId);
        return canCurrentActorUsePolicy(AccessPolicyKeys.USER_MEDIA_UPLOAD, normalizedOwnerUserId, normalizedOwnerUserId);
    }

    public boolean canCurrentActorImportMedia(String ownerUserId) {
        String normalizedOwnerUserId = normalizeId(ownerUserId);
        return canCurrentActorUsePolicy(AccessPolicyKeys.USER_MEDIA_IMPORT, normalizedOwnerUserId, normalizedOwnerUserId);
    }

    public boolean canCurrentActorSubmitPrivacyConsent(String userId) {
        String normalizedUserId = normalizeId(userId);
        return canCurrentActorUsePolicy(AccessPolicyKeys.PRIVACY_CONSENT_SUBMIT, normalizedUserId, normalizedUserId);
    }

    private boolean canCurrentActorUsePolicy(String policyKey, String subjectUserId, String resourceId) {
        String normalizedSubjectUserId = normalizeId(subjectUserId);
        String normalizedResourceId = normalizeId(resourceId);
        if (!StringUtils.hasText(normalizedSubjectUserId) || !StringUtils.hasText(normalizedResourceId)) {
            return false;
        }
        if (!requiresAuthenticatedActor()) {
            return true;
        }
        String actorUserId = resolveActorUserId(SecurityContextHolder.getContext().getAuthentication());
        return can(policyKey, actorUserId, normalizedSubjectUserId, normalizedResourceId);
    }

    @Override
    public boolean canAccessChat(String chatId, String actorUserId) {
        return chatPolicyFacts.canAccessChat(chatId, actorUserId);
    }

    @Override
    public boolean canManageSupportChat(String chatId, String actorUserId) {
        return chatPolicyFacts.canManageSupportChat(chatId, actorUserId);
    }

    @Override
    public boolean hasExplicitGrant(
            String subjectUserId,
            String permission,
            String resourceType,
            String resourceId,
            AccessGrantEffect expectedEffect) {
        AccessGrantEffect effect = currentCache().grants.get(new GrantKey(
                normalizeId(subjectUserId),
                safeText(permission),
                safeText(resourceType),
                normalizeId(resourceId)));
        return expectedEffect == effect;
    }

    private GrantKey grantKey(AccessGrant grant) {
        if (grant == null) {
            return null;
        }
        String subjectUserId = normalizeId(grant.getSubjectUserId());
        String permission = safeText(grant.getPermission());
        String resourceType = safeText(grant.getResourceType());
        String resourceId = normalizeId(grant.getResourceId());
        if (!StringUtils.hasText(subjectUserId)
                || !StringUtils.hasText(permission)
                || !StringUtils.hasText(resourceType)
                || !StringUtils.hasText(resourceId)) {
            return null;
        }
        return new GrantKey(subjectUserId, permission, resourceType, resourceId);
    }

    private AccessGrantEffect grantEffect(AccessGrant grant) {
        String effect = safeText(grant == null ? null : grant.getEffect()).toLowerCase(Locale.ROOT);
        if (EFFECT_DENY.equals(effect)) {
            return AccessGrantEffect.DENY;
        }
        if (EFFECT_ALLOW.equals(effect)) {
            return AccessGrantEffect.ALLOW;
        }
        return null;
    }

    private boolean isExpired(String expiresAtIso, Instant now) {
        String normalizedExpiresAtIso = safeText(expiresAtIso);
        if (!StringUtils.hasText(normalizedExpiresAtIso)) {
            return false;
        }
        try {
            return Instant.parse(normalizedExpiresAtIso).isBefore(now);
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean isPolicyEnabled(String status) {
        String normalizedStatus = safeText(status).toLowerCase(Locale.ROOT);
        return !"disabled".equals(normalizedStatus)
                && !"inactive".equals(normalizedStatus)
                && !"d".equals(normalizedStatus);
    }

    public boolean requiresAuthenticatedActor() {
        return !configProperties.isMemberFallbackEnabled() && !isDemoSessionRequest();
    }

    private boolean isDemoSessionRequest() {
        // WebSocket callbacks retain the routed session mode after the HTTP
        // handshake request has ended.
        if (MongoSessionModeContext.currentMode() == MongoSessionMode.DEMO) {
            return true;
        }
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return false;
        }
        return DemoSessionRequestSupport.isDemoSessionRequest(servletRequestAttributes.getRequest());
    }

    private String normalizeId(String value) {
        String normalizedValue = safeText(value);
        return StringUtils.hasText(normalizedValue) ? normalizedValue : null;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record GrantKey(
            String subjectUserId,
            String permission,
            String resourceType,
            String resourceId) {
    }
}
