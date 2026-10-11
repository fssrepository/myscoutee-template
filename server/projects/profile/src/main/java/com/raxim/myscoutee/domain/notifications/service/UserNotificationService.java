package com.raxim.myscoutee.domain.notifications.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.notifications.document.NotificationOutboxItem;
import com.raxim.myscoutee.domain.notifications.document.UserNotification;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationDto;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationPreferenceDto;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationReadResponseDto;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationsPageResponseDto;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationSyncKnownItemDto;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationsSyncRequestDto;
import com.raxim.myscoutee.domain.notifications.dto.NotificationDtos.UserNotificationsSyncResponseDto;
import com.raxim.myscoutee.domain.notifications.repository.UserNotificationPageRepository;
import com.raxim.myscoutee.domain.notifications.repository.UserNotificationPageRepository.UserNotificationCursor;
import com.raxim.myscoutee.domain.notifications.repository.UserNotificationPageRepository.UserNotificationSlice;
import com.raxim.myscoutee.domain.notifications.repository.UserNotificationsRepository;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.notifications.mapper.UserNotificationMapper;

@Service
public class UserNotificationService implements com.raxim.myscoutee.domain.notifications.api.NotificationInbox {
    private static final String BUCKET_NEW = "new";
    private static final String BUCKET_ALL = "all";
    private static final String CHAT_MESSAGE_KIND = "chat-message";
    private static final String CHAT_SOURCE_TYPE = "chat";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SYNC_ITEMS = 500;

    private final UserNotificationsRepository userNotificationsRepository;
    private final UserNotificationPageRepository userNotificationPageRepository;
    private final AccountNotifications accounts;
    private final UserNotificationMapper userNotificationMapper;

    public UserNotificationService(
            UserNotificationsRepository userNotificationsRepository,
            UserNotificationPageRepository userNotificationPageRepository,
            AccountNotifications accounts,
            UserNotificationMapper userNotificationMapper) {
        this.userNotificationsRepository = userNotificationsRepository;
        this.userNotificationPageRepository = userNotificationPageRepository;
        this.accounts = accounts;
        this.userNotificationMapper = userNotificationMapper;
    }

    public UserNotificationsPageResponseDto queryPage(
            String recipientUserId,
            String requestedBucket,
            Integer requestedLimit,
            String cursor) { return queryPage(recipientUserId, requestedBucket, requestedLimit, cursor, null); }

    public UserNotificationsPageResponseDto queryPage(String recipientUserId, String requestedBucket, Integer requestedLimit, String cursor, String workspace) {
        String normalizedRecipientUserId = requiredUserId(recipientUserId);
        String bucket = normalizeBucket(requestedBucket);
        int limit = normalizeLimit(requestedLimit);
        UserNotificationCursor parsedCursor = parseCursor(cursor, bucket);
        UserNotificationSlice slice = userNotificationPageRepository.findPage(
                normalizedRecipientUserId,
                BUCKET_NEW.equals(bucket),
                limit,
                parsedCursor, workspace);
        List<UserNotificationDto> records = userNotificationMapper.toDtos(slice.records());
        String nextCursor = slice.hasNext() && !slice.records().isEmpty()
                ? encodeCursor(bucket, slice.records().getLast())
                : null;
        int unreadCount = currentUnreadCount(normalizedRecipientUserId);
        return new UserNotificationsPageResponseDto(
                records,
                slice.total(),
                nextCursor,
                unreadCount,
                isMuted(normalizedRecipientUserId));
    }

    public UserNotificationReadResponseDto markRead(String recipientUserId, String notificationId) {
        String normalizedRecipientUserId = requiredUserId(recipientUserId);
        String normalizedNotificationId = requiredText(notificationId, "Notification id is required.");
        String nowIso = Instant.now().toString();
        var markedRead = userNotificationPageRepository.markRead(
                normalizedNotificationId,
                normalizedRecipientUserId,
                nowIso);
        UserNotification notification = markedRead.orElseGet(() -> userNotificationsRepository
                .findByIdAndRecipientUserId(normalizedNotificationId, normalizedRecipientUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification was not found.")));
        if (markedRead.isPresent()) {
            accounts.incrementUnreadCounter(normalizedRecipientUserId, -1);
        }
        int unreadCount = currentUnreadCount(normalizedRecipientUserId);
        return new UserNotificationReadResponseDto(
                userNotificationMapper.toDto(notification),
                unreadCount);
    }

    public UserNotificationsSyncResponseDto sync(
            String recipientUserId,
            UserNotificationsSyncRequestDto request) {
        String normalizedRecipientUserId = requiredUserId(recipientUserId);
        String bucket = normalizeBucket(request == null ? null : request.bucket());
        boolean unreadOnly = BUCKET_NEW.equals(bucket);
        String workspace = request == null ? null : request.workspace();
        List<UserNotificationSyncKnownItemDto> knownItems = request == null || request.knownItems() == null
                ? List.of()
                : request.knownItems().stream()
                        .filter(item -> item != null && StringUtils.hasText(item.id()))
                        .limit(MAX_SYNC_ITEMS + 1L)
                        .toList();
        if (knownItems.size() > MAX_SYNC_ITEMS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Notification sync supports at most " + MAX_SYNC_ITEMS + " cached items.");
        }

        Map<String, Long> knownRevisions = knownItems.stream()
                .collect(Collectors.toMap(
                        item -> item.id().trim(),
                        item -> Math.max(1L, item.revision() == null ? 1L : item.revision()),
                        (left, right) -> right,
                        LinkedHashMap::new));
        List<UserNotification> cachedRecords = userNotificationPageRepository.findByRecipientAndIds(
                normalizedRecipientUserId,
                List.copyOf(knownRevisions.keySet()));
        Map<String, UserNotification> cachedRecordsById = cachedRecords.stream()
                .collect(Collectors.toMap(
                        UserNotification::getId,
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new));

        LinkedHashSet<String> removedIds = new LinkedHashSet<>();
        LinkedHashMap<String, UserNotification> upsertsById = new LinkedHashMap<>();
        for (Map.Entry<String, Long> known : knownRevisions.entrySet()) {
            UserNotification current = cachedRecordsById.get(known.getKey());
            if (current == null || !matchesWorkspace(current, workspace) || (unreadOnly && StringUtils.hasText(current.getReadAtIso()))) {
                removedIds.add(known.getKey());
                continue;
            }
            if (notificationRevision(current) != known.getValue()) {
                upsertsById.put(current.getId(), current);
            }
        }

        var tail = request == null ? null : request.loadedTail();
        int windowLimit = Math.min(
                MAX_SYNC_ITEMS,
                knownItems.size() + normalizeLimit(request == null ? null : request.limit()));
        List<UserNotification> loadedWindow = userNotificationPageRepository.findLoadedWindow(
                normalizedRecipientUserId,
                unreadOnly,
                tail == null ? null : tail.createdAtIso(),
                tail == null ? null : tail.id(),
                windowLimit, workspace);
        for (UserNotification current : loadedWindow) {
            Long knownRevision = knownRevisions.get(current.getId());
            if (knownRevision == null || notificationRevision(current) != knownRevision) {
                upsertsById.put(current.getId(), current);
            }
        }

        UserNotificationSlice summary = userNotificationPageRepository.findPage(
                normalizedRecipientUserId,
                unreadOnly,
                1,
                null, workspace);
        return new UserNotificationsSyncResponseDto(
                userNotificationMapper.toDtos(List.copyOf(upsertsById.values())),
                List.copyOf(removedIds),
                summary.total(),
                currentUnreadCount(normalizedRecipientUserId),
                isMuted(normalizedRecipientUserId));
    }

    private boolean matchesWorkspace(UserNotification notification, String workspace) {
        if (workspace == null || workspace.isBlank() || "all".equals(workspace)) return true;
        String groupId = notification.getPayload() == null ? null : notification.getPayload().get("workspaceGroupId");
        return "main".equals(workspace) ? !StringUtils.hasText(groupId) : workspace.equals(groupId);
    }

    public int markChatMessagesRead(String recipientUserId, String chatId, List<String> messageIds) {
        String normalizedRecipientUserId = notificationAccountId(recipientUserId);
        String normalizedChatId = safeText(chatId);
        List<String> normalizedMessageIds = compactIds(messageIds);
        if (!StringUtils.hasText(normalizedRecipientUserId)
                || !StringUtils.hasText(normalizedChatId)
                || normalizedMessageIds.isEmpty()) {
            return 0;
        }
        return applyChatNotificationReadCount(
                normalizedRecipientUserId,
                userNotificationPageRepository.markUnreadChatMessagesRead(
                        normalizedRecipientUserId,
                        normalizedChatId,
                        normalizedMessageIds,
                        Instant.now().toString()));
    }

    public int markChatRead(String recipientUserId, String chatId) {
        String normalizedRecipientUserId = notificationAccountId(recipientUserId);
        String normalizedChatId = safeText(chatId);
        if (!StringUtils.hasText(normalizedRecipientUserId) || !StringUtils.hasText(normalizedChatId)) {
            return 0;
        }
        return applyChatNotificationReadCount(
                normalizedRecipientUserId,
                userNotificationPageRepository.markUnreadBySource(
                        normalizedRecipientUserId,
                        CHAT_MESSAGE_KIND,
                        CHAT_SOURCE_TYPE,
                        normalizedChatId,
                        Instant.now().toString()));
    }

    private String notificationAccountId(String userId) {
        String id = safeText(userId);
        return accounts.notificationAccountId(id);
    }

    private int applyChatNotificationReadCount(String recipientUserId, long changed) {
        int changedCount = safeInt(changed);
        if (changedCount > 0) {
            accounts.incrementUnreadCounter(recipientUserId, -changedCount);
        }
        return changedCount;
    }

    public UserNotificationPreferenceDto updateMutedPreference(String recipientUserId, Boolean muted) {
        String normalizedRecipientUserId = requiredUserId(recipientUserId);
        if (muted == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Muted preference is required.");
        }
        accounts.setMuted(normalizedRecipientUserId, muted);
        return new UserNotificationPreferenceDto(muted);
    }

    public void materialize(NotificationOutboxItem outboxItem) {
        if (outboxItem == null) {
            return;
        }
        List<String> recipientUserIds = compactIds(outboxItem.getRecipientUserIds());
        if (recipientUserIds.isEmpty()) {
            return;
        }
        String outboxKey = firstNonBlank(outboxItem.getSyncKey(), outboxItem.getId());
        if (!StringUtils.hasText(outboxKey)) {
            return;
        }

        String nowIso = Instant.now().toString();
        String createdDate = firstNonBlank(outboxItem.getCreatedDate(), nowIso);
        Map<String, String> payload = normalizedPayload(outboxItem.getPayload());
        for (String recipientUserId : recipientUserIds) {
            UserNotification notification = toUserNotification(
                    outboxItem,
                    recipientUserId,
                    payload,
                    createdDate,
                    nowIso,
                    outboxKey);
            boolean incrementUnread;
            if (StringUtils.hasText(notification.getAggregationKey())) {
                incrementUnread = userNotificationPageRepository
                        .upsertAggregated(notification)
                        .becameUnread();
            } else {
                incrementUnread = userNotificationPageRepository.insertIfAbsent(notification);
            }
            if (incrementUnread) {
                accounts.incrementUnreadCounter(recipientUserId, 1);
            }
        }
    }

    public int currentUnreadCount(String recipientUserId) {
        String normalizedRecipientUserId = requiredUserId(recipientUserId);
        return safeInt(userNotificationPageRepository.countUnread(normalizedRecipientUserId));
    }

    private UserNotification toUserNotification(
            NotificationOutboxItem outboxItem,
            String recipientUserId,
            Map<String, String> payload,
            String createdDate,
            String nowIso,
            String outboxKey) {
        String kind = safeText(outboxItem.getNotificationType());
        String sourceType = safeText(outboxItem.getEntityType());
        String sourceId = safeText(outboxItem.getEntityId());
        String senderUserId = firstNonBlank(
                payload.get("senderUserId"),
                payload.get("actorUserId"),
                outboxItem.getCreatedUser());
        UserNotification notification = new UserNotification();
        notification.setId(new ObjectId().toHexString());
        notification.setSyncKey(outboxKey + ":" + recipientUserId);
        String aggregationGroup = safeText(payload.get("notification_aggregation_key"));
        notification.setAggregationKey(StringUtils.hasText(aggregationGroup)
                ? recipientUserId + ":" + aggregationGroup
                : null);
        notification.setOutboxId(nullableText(outboxItem.getId()));
        notification.setRecipientUserId(recipientUserId);
        notification.setKind(kind);
        notification.setCategory(resolveCategory(kind, outboxItem.getRecipientScope(), sourceType));
        notification.setTitle(firstNonBlank(outboxItem.getTitle(), payload.get("title"), "Notification"));
        notification.setMessage(firstNonBlank(
                outboxItem.getBody(),
                payload.get("body"),
                "You have a new update."));
        notification.setSenderUserId(nullableText(senderUserId));
        notification.setActionPath(toInternalActionPath(firstNonBlank(
                outboxItem.getClickUrl(),
                payload.get("url"),
                "/game")));
        notification.setSourceType(sourceType);
        notification.setSourceId(sourceId);
        notification.setPayload(payload);
        notification.setOccurrenceCount(1);
        notification.setRevision(1L);
        notification.setReadAtIso(null);
        notification.setCreatedDate(createdDate);
        notification.setCreatedUser(nullableText(outboxItem.getCreatedUser()));
        notification.setUpdatedDate(nowIso);
        notification.setUpdatedUser(nullableText(outboxItem.getCreatedUser()));
        return notification;
    }

    private long notificationRevision(UserNotification notification) {
        return notification == null || notification.getRevision() == null
                ? 1L
                : Math.max(1L, notification.getRevision());
    }

    private String resolveCategory(String kind, String recipientScope, String sourceType) {
        if ("case-reminder".equals(kind) || "announcement-closed".equals(kind)) return "scheduled";
        if ("event-member-approved".equals(safeText(kind).toLowerCase(Locale.ROOT))) {
            return "event-admin";
        }
        String haystack = String.join(" ",
                safeText(kind),
                safeText(recipientScope),
                safeText(sourceType)).toLowerCase(Locale.ROOT);
        if (haystack.contains("scheduled")
                || haystack.contains("scheduler")
                || haystack.contains("random-group")
                || haystack.contains("random-room")) {
            return "scheduled";
        }
        if (haystack.contains("app-admin")
                || haystack.contains("admin-param")
                || haystack.contains("moderation")) {
            return "app-admin";
        }
        if (haystack.contains("asset")) {
            return "asset";
        }
        if (haystack.contains("chat")) {
            return "chat";
        }
        if (haystack.contains("event")) {
            if (haystack.contains("manager")
                    || haystack.contains("admin")
                    || haystack.contains("stage")
                    || haystack.contains("tournament")
                    || haystack.contains("ticketing")
                    || haystack.contains("checkout")) {
                return "event-admin";
            }
            return "event";
        }
        return "user";
    }

    private String toInternalActionPath(String clickUrl) {
        String candidate = firstNonBlank(clickUrl, "/game");
        try {
            URI uri = URI.create(candidate);
            String path = safeText(uri.getRawPath());
            if (!StringUtils.hasText(path)) {
                return "/game";
            }
            StringBuilder actionPath = new StringBuilder(path.startsWith("/") ? path : "/" + path);
            if (StringUtils.hasText(uri.getRawQuery())) {
                actionPath.append('?').append(uri.getRawQuery());
            }
            if (StringUtils.hasText(uri.getRawFragment())) {
                actionPath.append('#').append(uri.getRawFragment());
            }
            return actionPath.toString();
        } catch (IllegalArgumentException ignored) {
            return "/game";
        }
    }

    private UserNotificationCursor parseCursor(String cursor, String bucket) {
        String normalized = safeText(cursor);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(normalized),
                    StandardCharsets.UTF_8);
            String[] fields = decoded.split("\\n", -1);
            if (fields.length != 3
                    || !bucket.equals(normalizeBucket(fields[0]))
                    || !StringUtils.hasText(fields[1])
                    || !StringUtils.hasText(fields[2])) {
                throw new IllegalArgumentException("Cursor does not match the notification bucket.");
            }
            Instant.parse(fields[1]);
            return new UserNotificationCursor(bucket, fields[1].trim(), fields[2].trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification cursor.", exception);
        }
    }

    private String encodeCursor(String bucket, UserNotification notification) {
        String value = bucket
                + "\n"
                + safeText(notification == null ? null : notification.getCreatedDate())
                + "\n"
                + safeText(notification == null ? null : notification.getId());
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isMuted(String recipientUserId) {
        return accounts.muted(recipientUserId);
    }

    private String normalizeBucket(String bucket) {
        String normalized = safeText(bucket).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return BUCKET_NEW;
        }
        if (!BUCKET_NEW.equals(normalized) && !BUCKET_ALL.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification bucket must be new or all.");
        }
        return normalized;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(MAX_PAGE_SIZE, limit));
    }

    private List<String> compactIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> compact = new LinkedHashSet<>();
        values.stream()
                .map(this::safeText)
                .filter(StringUtils::hasText)
                .forEach(compact::add);
        return new ArrayList<>(compact);
    }

    private Map<String, String> normalizedPayload(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            String normalizedKey = safeText(key);
            if (StringUtils.hasText(normalizedKey)) {
                normalized.put(normalizedKey, safeText(value));
            }
        });
        return Map.copyOf(normalized);
    }

    private String requiredUserId(String userId) {
        return requiredText(userId, "Current user is required.");
    }

    private String requiredText(String value, String message) {
        String normalized = safeText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private int safeInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullableText(String value) {
        String normalized = safeText(value);
        return StringUtils.hasText(normalized) ? normalized : null;
    }
}
