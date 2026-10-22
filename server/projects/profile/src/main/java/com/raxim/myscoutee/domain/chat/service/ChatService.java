package com.raxim.myscoutee.domain.chat.service;




import com.raxim.myscoutee.domain.accounts.api.ProfileReadState;
import com.raxim.myscoutee.domain.administration.api.AdministratorClassification;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;

import com.raxim.myscoutee.domain.chat.document.ContactChatAccess;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;

import jakarta.annotation.PreDestroy;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.chat.document.Chat;

import com.raxim.myscoutee.domain.chat.document.ChatMessage;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatAvatarRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatMessageAttachmentRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatMessageReactionRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatMessageReplyRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatReadAvatarRecord;
import com.raxim.myscoutee.domain.chat.document.Chat.SupportCaseAssigneeRecord;
import com.raxim.myscoutee.domain.chat.document.Chat.SupportCaseRecord;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDeviceDeleteRequestDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDeviceUpsertRequestDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatHeaderSyncResponseDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMemberDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMembersPageResponseDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageAttachmentDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageReplyDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessagesPageResponseDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatPageResponseDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatReadAvatarDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatReadReceiptDto;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserMenuCountersDto;
import com.raxim.myscoutee.domain.chat.repository.ChatMessagesRepository;
import com.raxim.myscoutee.domain.chat.repository.ChatsRepository;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.access.api.AccessPolicyKeys;
import com.raxim.myscoutee.domain.chat.mapper.ChatMapper;
import com.raxim.myscoutee.domain.chat.mapper.ChatMessageMapper;

import com.raxim.myscoutee.domain.notifications.api.NotificationInbox;

@Service
public class ChatService implements com.raxim.myscoutee.domain.chat.api.ChatQueries {
    private final com.raxim.myscoutee.domain.accounts.api.AccountDevices accountDevices;
    private final com.raxim.myscoutee.domain.accounts.api.ChatCounters chatCounters;
    private final com.raxim.myscoutee.domain.accounts.api.SelectedProfile selectedProfile;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
    private static final DateTimeFormatter CHAT_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            .withZone(ZoneId.systemDefault());
    private static final String SUPPORT_CASE_STATUS_PENDING = "pending";
    private static final String SUPPORT_CASE_STATUS_WARNED = "warned";
    private static final String SUPPORT_CASE_STATUS_PICKED = "picked";
    private static final String SUPPORT_CASE_STATUS_SOLVED = "solved";
    private static final String SUPPORT_CASE_STATUS_BLOCKED = "blocked";
    private static final String SUPPORT_CASE_FILTER_ALL = "all";
    private static final String CHAT_CHANNEL_APP_SUPPORT = "appSupport";
    private static final String CHAT_CHANNEL_SUPPORT_CASE = "supportCase";
    private static final String MEMBER_STATUS_ACCEPTED = "accepted";
    private static final String MEMBER_STATUS_PENDING = "pending";
    private static final int DEFAULT_CHAT_MESSAGES_PAGE_SIZE = 20;
    private static final int MAX_CHAT_MESSAGES_PAGE_SIZE = 100;

    private final ChatsRepository chatsRepository;
    private final ChatMessagesRepository chatMessagesRepository;
    private final MongoTemplate mongoTemplate;
    private final ProfileSummaries profileDirectory;
    private final ProfileReadState profilePresentation;
    private final ChatStreamPublisher chatStreamPublisher;
    private final ChatPushNotificationService chatPushNotificationService;
    private final NotificationInbox userNotificationService;
    private final OperationalTelemetry adminTelemetryService;
    private final AdministratorClassification adminMenuCounterService;
    private final AccessPermissions accessControlService;
    private final ChatMapper chatMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ExecutorService chatWriteExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatService(
            ChatsRepository chatsRepository,
            ChatMessagesRepository chatMessagesRepository,
            MongoTemplate mongoTemplate,
            ProfileSummaries profileDirectory,
            ProfileReadState profilePresentation,
            ChatStreamPublisher chatStreamPublisher,
            ChatPushNotificationService chatPushNotificationService,
            NotificationInbox userNotificationService,
            OperationalTelemetry adminTelemetryService,
            AdministratorClassification adminMenuCounterService,
            AccessPermissions accessControlService,
            ChatMapper chatMapper,
            ChatMessageMapper chatMessageMapper,
            com.raxim.myscoutee.domain.accounts.api.AccountDevices accountDevices,
            com.raxim.myscoutee.domain.accounts.api.ChatCounters chatCounters,
            com.raxim.myscoutee.domain.accounts.api.SelectedProfile selectedProfile) {
        this.accountDevices = accountDevices;
        this.chatCounters = chatCounters;
        this.selectedProfile = selectedProfile;

        this.chatsRepository = chatsRepository;
        this.chatMessagesRepository = chatMessagesRepository;
        this.mongoTemplate = mongoTemplate;
        this.profileDirectory = profileDirectory;
        this.profilePresentation = profilePresentation;
        this.chatStreamPublisher = chatStreamPublisher;
        this.chatPushNotificationService = chatPushNotificationService;
        this.userNotificationService = userNotificationService;
        this.adminTelemetryService = adminTelemetryService;
        this.adminMenuCounterService = adminMenuCounterService;
        this.accessControlService = accessControlService;
        this.chatMapper = chatMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @PreDestroy
    void shutdownChatWriteExecutor() {
        chatWriteExecutor.shutdownNow();
    }

    public List<ChatDto> queryChats(String requestedUserId) {
        return queryChatPage(
                requestedUserId,
                "all",
                "date",
                "desc",
                null,
                null,
                MAX_CHAT_MESSAGES_PAGE_SIZE,
                null,
                null,
                false,
                null).items();
    }

    public ChatDto queryChatById(String chatId, String requestedUserId) {
        String userId = resolveChatActor(chatId, requireResolvedUserId(requestedUserId));
        ChatDocumentsSnapshot snapshot = requireChat(chatId, userId);
        Chat chat = snapshot.viewerChat() == null
                ? snapshot.chatDocuments().get(0)
                : snapshot.viewerChat();
        return toChatDto(chat, chatMemberUsers(List.of(chat)));
    }

    public ChatHeaderSyncResponseDto syncChatHeader(
            String chatId,
            String requestedUserId,
            Long knownRevision) {
        String userId = resolveChatActor(chatId, requireResolvedUserId(requestedUserId));
        String normalizedChatId = normalizeId(chatId);
        if (!StringUtils.hasText(normalizedChatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat id is required.");
        }
        Chat chat = chatsRepository.findByOwnerUserIdAndChatId(userId, normalizedChatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found."));
        long revision = normalizeChatRevision(chat.getRevision());
        boolean changed = knownRevision == null || knownRevision.longValue() != revision;
        UserMenuCountersDto counters = profilePresentation.queryMenuCounters(userId);
        return new ChatHeaderSyncResponseDto(
                revision,
                changed,
                StringUtils.hasText(safeText(chat.getOwnerStatus())) ? safeText(chat.getOwnerStatus()) : null,
                normalizeCount(chat.getUnread()),
                safeText(chat.getLastMessage()),
                StringUtils.hasText(normalizeId(chat.getLastSenderId())) ? normalizeId(chat.getLastSenderId()) : null,
                StringUtils.hasText(safeText(chat.getDateIso())) ? safeText(chat.getDateIso()) : null,
                StringUtils.hasText(safeText(chat.getContextStartAtIso())) ? safeText(chat.getContextStartAtIso()) : null,
                StringUtils.hasText(safeText(chat.getContextEndAtIso())) ? safeText(chat.getContextEndAtIso()) : null,
                normalizeCount(counters.chats()),
                counters.chat());
    }

    public ChatPageResponseDto queryChatPage(
            String requestedUserId,
            String contextFilterValue,
            String sortValue,
            String sortDirectionValue,
            String secondaryFilterValue,
            String cursor,
            Integer limitValue,
            String rangeStartIso,
            String rangeEndIso,
            Boolean adminServiceOnly,
            String supportCaseFilterValue) {
        String userId = resolveUserId(requestedUserId);
        if (!StringUtils.hasText(userId)) {
            return chatPageResponse(userId, List.of(), 0, null);
        }

        String contextFilter = normalizeChatContextFilter(contextFilterValue);
        String sort = normalizeChatQuerySort(sortValue, secondaryFilterValue);
        boolean sortAscending = resolveChatSortAscending(sort, sortDirectionValue);
        long rangeStartMs = parseIsoMillis(rangeStartIso, Long.MIN_VALUE);
        long rangeEndMs = parseIsoMillis(rangeEndIso, Long.MAX_VALUE);
        if (rangeStartMs > rangeEndMs) {
            return chatPageResponse(userId, List.of(), 0, null);
        }
        if (Boolean.TRUE.equals(adminServiceOnly) && "service".equals(contextFilter)) {
            return querySupportCasePage(
                    userId,
                    sort,
                    sortAscending,
                    cursor,
                    limitValue,
                    rangeStartMs,
                    rangeEndMs,
                    supportCaseFilterValue);
        }

        int limit = normalizeChatPageLimit(limitValue);
        int offset = parseChatCursorOffset(cursor);
        Criteria criteria = chatPageCriteria(
                userId,
                contextFilter,
                rangeStartIso,
                rangeEndIso,
                false,
                null);
        long total = mongoTemplate.count(new Query(criteria), Chat.class);
        Query pageQuery = new Query(criteria)
                .with(chatPageSort(sort, sortAscending))
                .skip(offset)
                .limit(limit);
        List<ChatDto> items = chatDtos(mongoTemplate.find(pageQuery, Chat.class));
        int endOffset = offset + items.size();
        String nextCursor = endOffset < total && !items.isEmpty()
                ? Integer.toString(endOffset)
                : null;
        return chatPageResponse(userId, items, safePageTotal(total), nextCursor);
    }

    public ChatDto ensureContactChat(String requestedUserId, String targetUserId) {
        String actorId = accountUserId(requireResolvedUserId(requestedUserId));
        String targetId = normalizeId(targetUserId);
        Map<String, ProfileSummary> users = requireContactTargets(actorId, List.of(targetId));
        List<String> participantIds = new ArrayList<>(List.of(actorId, targetId));
        participantIds.sort(String::compareTo);
        String chatId = "c-contact-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(String.join("\n", participantIds).getBytes(StandardCharsets.UTF_8));
        // Only initialize missing owner copies: reopening must never replace messages or unread state.
        for (String ownerId : participantIds) {
            String otherId = ownerId.equals(actorId) ? targetId : actorId;
            ProfileSummary other = users.get(otherId);
            mongoTemplate.upsert(Query.query(Criteria.where("_id").is(buildMongoId(ownerId, chatId))), new Update()
                    .setOnInsert("chatId", chatId).setOnInsert("ownerUserId", ownerId)
                    .setOnInsert("channelType", "contact").setOnInsert("memberIds", participantIds)
                    .setOnInsert("title", safeText(other.getName())).setOnInsert("avatar", resolveUserInitials(other, otherId))
                    .setOnInsert("lastMessage", "").setOnInsert("lastSenderId", "")
                    .setOnInsert("unread", 0).setOnInsert("revision", 1L)
                    .setOnInsert("dateIso", Instant.now().toString()), Chat.class);
        }
        return queryChatById(chatId, actorId);
    }

    private Map<String, ProfileSummary> requireContactTargets(String actorId, List<String> targetIds) {
        if (targetIds.isEmpty() || targetIds.size() > 100 || targetIds.contains(actorId)
                || targetIds.stream().anyMatch(id -> !StringUtils.hasText(id)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select another contact.");
        for (String targetId : targetIds) requireContactChatConsent(actorId, targetId);
        Set<String> participantIds = new LinkedHashSet<>(targetIds); participantIds.add(actorId);
        Map<String, ProfileSummary> users = new LinkedHashMap<>();
        for (ProfileSummary user : profileDirectory.findByUserIdIn(participantIds)) {
            if (!StringUtils.hasText(user.getDeletedAtIso()) && !"deleted".equalsIgnoreCase(user.getProfileStatus()))
                users.put(user.getUserId(), user);
        }
        ProfileSummary actor = users.get(actorId);
        if (targetIds.stream().anyMatch(id -> actor == null || users.get(id) == null || !Objects.equals(actor.getWorkspaceGroupId(), users.get(id).getWorkspaceGroupId())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact unavailable.");
        return users;
    }

    private ChatPageResponseDto querySupportCasePage(
            String userId,
            String sort,
            boolean sortAscending,
            String cursor,
            Integer limitValue,
            long rangeStartMs,
            long rangeEndMs,
            String supportCaseFilterValue) {
        ProfileSummary actor = findUser(userId);
        if (!isAdminUser(actor)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required.");
        }
        int limit = normalizeChatPageLimit(limitValue);
        int offset = parseChatCursorOffset(cursor);
        String supportCaseFilter = normalizeSupportCaseFilter(supportCaseFilterValue);
        List<AggregationOperation> operations = new ArrayList<>();
        operations.add(context -> new Document("$match", new Document("$and", List.of(
                new Document("channelType", new Document(
                        "$in",
                        List.of(CHAT_CHANNEL_APP_SUPPORT, CHAT_CHANNEL_SUPPORT_CASE))),
                new Document("supportCase.status", new Document("$exists", true)),
                new Document("id", new Document("$type", "string").append("$ne", ""))))));
        operations.add(context -> new Document("$match", new Document("supportBaseGroupId", actor.getWorkspaceGroupId())));
        operations.add(context -> new Document("$addFields", new Document(
                "_supportViewerCopy",
                new Document("$cond", List.of(
                        new Document("$eq", List.of("$ownerUserId", userId)),
                        1,
                        0)))));
        operations.add(context -> new Document("$sort", new Document()
                .append("_supportViewerCopy", -1)
                .append("dateIso", -1)
                .append("_id", 1)));
        operations.add(context -> new Document("$group", new Document()
                .append("_id", "$id")
                .append("selected", new Document("$first", "$$ROOT"))));
        operations.add(context -> new Document("$replaceRoot", new Document("newRoot", "$selected")));
        Document selectedCaseMatch = supportCaseSelectionMatchDocument(
                rangeStartMs,
                rangeEndMs,
                supportCaseFilter);
        if (!selectedCaseMatch.isEmpty()) {
            operations.add(context -> new Document("$match", selectedCaseMatch));
        }
        operations.add(context -> new Document("$sort", chatPageSortDocument(sort, sortAscending)));
        operations.add(context -> new Document("$facet", new Document()
                .append("metadata", List.of(new Document("$count", "total")))
                .append("items", List.of(
                        new Document("$skip", offset),
                        new Document("$limit", limit)))));

        AggregationResults<Document> aggregationResults = mongoTemplate.aggregate(
                Aggregation.newAggregation(operations),
                "chats",
                Document.class);
        Document aggregationResult = aggregationResults.getUniqueMappedResult();
        List<Document> metadata = aggregationDocuments(
                aggregationResult == null ? null : aggregationResult.get("metadata"));
        int total = metadata.isEmpty()
                ? 0
                : safePageTotal(aggregationNumber(metadata.get(0).get("total")));
        List<Chat> pageRecords = aggregationDocuments(
                aggregationResult == null ? null : aggregationResult.get("items")).stream()
                .map(document -> mongoTemplate.getConverter().read(Chat.class, document))
                .filter(Objects::nonNull)
                .toList();
        List<ChatDto> items = chatDtos(pageRecords);
        int endOffset = offset + items.size();
        String nextCursor = endOffset < total && !items.isEmpty()
                ? Integer.toString(endOffset)
                : null;
        return chatPageResponse(userId, items, total, nextCursor);
    }

    private ChatPageResponseDto chatPageResponse(
            String userId,
            List<ChatDto> items,
            int total,
            String nextCursor) {
        UserMenuCountersDto counters = profilePresentation.queryMenuCounters(userId);
        return new ChatPageResponseDto(
                items,
                total,
                nextCursor,
                normalizeCount(counters.chats()),
                counters.chat());
    }


    public ChatMessagesPageResponseDto queryMessagesPage(
            String chatId,
            String requestedUserId,
            Integer limit,
            String cursor) {
        return queryMessagesPage(chatId, requestedUserId, limit, cursor, null);
    }

    public ChatMessagesPageResponseDto queryMessagesPage(
            String chatId, String requestedUserId, Integer limit, String cursor,
            String targetMessageId) {
        return queryMessagesPage(chatId, requestedUserId, limit, cursor, targetMessageId, true);
    }

    /** Integration reads do not imply that the person has viewed the messages. */
    public ChatMessagesPageResponseDto queryMessagesPage(
            String chatId, String requestedUserId, Integer limit, String cursor,
            String targetMessageId, boolean markViewed) {
        String userId = resolveChatActor(chatId, requireResolvedUserId(requestedUserId));
        ChatDocumentsSnapshot snapshot = requireChat(chatId, userId);
        String targetId = normalizeId(targetMessageId);
        ChatMessage target = StringUtils.hasText(targetId)
                ? chatMessagesRepository.findByChatIdAndMessageId(snapshot.chatId(), targetId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat message not found."))
                : null;
        int pageSize = normalizeChatMessagesPageSize(limit);
        int startIndex = normalizeChatMessagesCursor(cursor);
        if (target != null && !StringUtils.hasText(cursor)) {
            int newer = normalizeLongCount(chatMessagesRepository.countByChatIdAndSentAtIsoGreaterThan(
                    snapshot.chatId(), target.getSentAtIso()));
            startIndex = (newer / pageSize) * pageSize;
        }
        int total = normalizeLongCount(chatMessagesRepository.countByChatId(snapshot.chatId()));
        if (startIndex >= total) {
            return new ChatMessagesPageResponseDto(
                    List.of(),
                    total,
                    null,
                    null,
                    userNotificationService.currentUnreadCount(userId));
        }
        List<ChatMessage> messages = chatMessagesRepository
                .findByChatIdOrderBySentAtIsoDesc(snapshot.chatId(), PageRequest.of(startIndex / pageSize, pageSize))
                .stream()
                .filter(Objects::nonNull)
                .toList();
        List<String> loadedMessageIds = messages.stream()
                .map(ChatMessage::getId)
                .map(this::normalizeId)
                .filter(StringUtils::hasText)
                .toList();
        ChatReadUpdate readUpdate = !markViewed ? null : target != null
                ? markMessagesRead(snapshot, userId, List.of(targetId), messages)
                : startIndex == 0
                        ? markChannelRead(snapshot, userId, messages)
                        : markMessagesRead(snapshot, userId, loadedMessageIds, messages);
        if (readUpdate != null && readUpdate.changed() && !readUpdate.readMessageIds().isEmpty()) {
            publishChatEvent(toReadEvent(snapshot.chatId(), readUpdate));
        }
        List<ChatMessageDto> items = chatMessageMapper.toDtos(messages, userId);
        int nextIndex = startIndex + items.size();
        return new ChatMessagesPageResponseDto(
                items,
                total,
                nextIndex < total ? String.valueOf(nextIndex) : null,
                readUpdate == null ? null : toReadReceiptDto(readUpdate),
                userNotificationService.currentUnreadCount(userId));
    }

    public ChatMembersPageResponseDto queryMembersPage(
            String chatId,
            String requestedUserId,
            Integer limitValue,
            String cursor,
            Boolean pendingOnly) {
        String userId = resolveChatActor(chatId, requireResolvedUserId(requestedUserId));
        ChatDocumentsSnapshot snapshot = requireChat(chatId, userId);
        Chat memberSource = snapshot.viewerChat() == null
                ? snapshot.chatDocuments().get(0)
                : snapshot.viewerChat();
        int limit = normalizeChatPageLimit(limitValue);
        int offset = parseChatCursorOffset(cursor);
        List<String> memberIds = safeMemberIds(memberSource.getMemberIds()).stream()
                .map(this::normalizeId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (memberIds.isEmpty()) {
            return new ChatMembersPageResponseDto(List.of(), 0, null);
        }

        long total;
        List<String> pageMemberIds;
        if (Boolean.TRUE.equals(pendingOnly)) {
            return new ChatMembersPageResponseDto(List.of(), 0, null);
        } else {
            total = profileDirectory.countByUserIdIn(memberIds);
            pageMemberIds = memberIds;
        }
        List<ChatMemberDto> items = profileDirectory.pageByUserIdIn(pageMemberIds, offset, limit).stream()
                .map(user -> chatMapper.toMemberDto(
                        user,
                        MEMBER_STATUS_ACCEPTED))
                .filter(Objects::nonNull)
                .toList();
        int nextOffset = offset + items.size();
        return new ChatMembersPageResponseDto(
                items,
                safePageTotal(total),
                nextOffset < total && !items.isEmpty() ? Integer.toString(nextOffset) : null);
    }

    public ChatMessageDto sendMessage(String chatId, String requestedUserId, String text) {
        return sendMessage(chatId, requestedUserId, text, null, null, List.of());
    }

    public ChatMessageDto sendMessage(String chatId, String requestedUserId, String text, String clientId) {
        return sendMessage(chatId, requestedUserId, text, clientId, null, List.of());
    }

    public ChatMessageDto postSystemMessage(String chatId, String text, String systemType) {
        String normalizedChatId = normalizeId(chatId);
        String normalizedText = normalizeText(text);
        if (!StringUtils.hasText(normalizedChatId) || !StringUtils.hasText(normalizedText)) {
            return null;
        }
        List<Chat> chatDocuments = ensureOwnerCopies(chatsRepository.findByChatId(normalizedChatId));
        if (chatDocuments.isEmpty()) {
            return null;
        }

        ChatMessage message = buildSystemMessage(normalizedText, systemType);
        ChatStreamEvent streamEvent = chatMessageMapper.toStreamEvent(normalizedChatId, message);
        CompletableFuture<Boolean> redisPublish = publishChatEventAsync(streamEvent);
        message.setChatId(normalizedChatId);
        message.setMongoId(ChatMessage.buildMongoId(normalizedChatId, message.getId()));
        message.refreshMessageSearchFields();
        chatMessagesRepository.save(message);
        List<Chat> updatedDocuments = updateChatSummaries(chatDocuments, message, "", false);
        observeChatEventPublish(redisPublish, streamEvent);
        chatPushNotificationService.notifyRecipients(updatedDocuments, streamEvent);
        adminTelemetryService.record("chat.message.system", "chats", normalizedChatId, "system");
        return chatMessageMapper.toDto(message, "");
    }

    public ChatMessageDto ensureInitialSystemMessage(String chatId, String text, String systemType) {
        return ensureInitialSystemMessageResult(chatId, text, systemType).message();
    }

    private InitialSystemMessageResult ensureInitialSystemMessageResult(
            String chatId,
            String text,
            String systemType) {
        String normalizedChatId = normalizeId(chatId);
        String normalizedText = normalizeText(text);
        String normalizedSystemType = firstNonBlank(systemType, "welcome");
        if (!StringUtils.hasText(normalizedChatId) || !StringUtils.hasText(normalizedText)) {
            return new InitialSystemMessageResult(null, false);
        }
        List<Chat> chatDocuments = ensureOwnerCopies(chatsRepository.findByChatId(normalizedChatId));
        if (chatDocuments.isEmpty()) {
            return new InitialSystemMessageResult(null, false);
        }

        String messageId = "system:" + normalizedSystemType;
        ChatMessage existing = chatMessagesRepository
                .findByChatIdAndMessageId(normalizedChatId, messageId)
                .orElse(null);
        if (existing != null) {
            return new InitialSystemMessageResult(chatMessageMapper.toDto(existing, ""), false);
        }

        ChatMessage message = buildSystemMessage(normalizedText, normalizedSystemType);
        message.setId(messageId);
        message.setSender("Templates System");
        message.setChatId(normalizedChatId);
        message.setMongoId(ChatMessage.buildMongoId(normalizedChatId, messageId));
        message.refreshMessageSearchFields();
        try {
            chatMessagesRepository.insert(message);
        } catch (DuplicateKeyException duplicate) {
            ChatMessage concurrentlyInserted = chatMessagesRepository
                    .findByChatIdAndMessageId(normalizedChatId, messageId)
                    .orElseThrow(() -> duplicate);
            return new InitialSystemMessageResult(chatMessageMapper.toDto(concurrentlyInserted, ""), false);
        }

        ChatStreamEvent streamEvent = chatMessageMapper.toStreamEvent(normalizedChatId, message);
        CompletableFuture<Boolean> redisPublish = publishChatEventAsync(streamEvent);
        List<Chat> updatedDocuments = updateChatSummaries(chatDocuments, message, "", false);
        observeChatEventPublish(redisPublish, streamEvent);
        chatPushNotificationService.notifyRecipients(updatedDocuments, streamEvent);
        adminTelemetryService.record(
                "chat.message.system.initial",
                "chats",
                normalizedChatId,
                normalizedSystemType);
        return new InitialSystemMessageResult(chatMessageMapper.toDto(message, ""), true);
    }

    public ChatMessageDto sendMessage(
            String chatId,
            String requestedUserId,
            String text,
            String clientId,
            ChatMessageReplyDto replyTo,
            List<ChatMessageAttachmentDto> attachments) {
        String userId = resolveChatActor(chatId, requireResolvedUserId(requestedUserId));
        String normalizedText = normalizeText(text);
        List<ChatMessageAttachmentRecord> normalizedAttachments = normalizeAttachments(attachments);
        if (!StringUtils.hasText(normalizedText) && normalizedAttachments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message text is required.");
        }
        String normalizedChatId = normalizeId(chatId);
        if (!StringUtils.hasText(normalizedChatId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found.");
        }
        if (!accessControlService.canCurrentActorCreateChatMessage(userId, normalizedChatId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user is not allowed to post in this chat.");
        }

        ChatDocumentsSnapshot accessSnapshot = requireChatAccessForSend(normalizedChatId, userId);
        ProfileSummary sender = findUser(userId);
        ChatMessage message = buildMessage(sender, userId, normalizedText, clientId, normalizeReply(replyTo), normalizedAttachments);
        return persistChatMessage(accessSnapshot, userId, message);
    }


    private ChatMessageDto persistChatMessage(
            ChatDocumentsSnapshot accessSnapshot,
            String userId,
            ChatMessage message) {
        List<Chat> chatDocuments = chatDocumentsForMutation(accessSnapshot, userId);
        message.setChatId(accessSnapshot.chatId());
        message.setMongoId(ChatMessage.buildMongoId(accessSnapshot.chatId(), message.getId()));
        message.refreshMessageSearchFields();
        if (StringUtils.hasText(message.getClientId())) {
            try { chatMessagesRepository.insert(message); }
            catch (DuplicateKeyException duplicate) {
                return chatMessageMapper.toDto(chatMessagesRepository.findByChatIdAndMessageId(
                        accessSnapshot.chatId(), message.getId()).orElseThrow(), userId);
            }
        } else chatMessagesRepository.save(message);
        ChatStreamEvent streamEvent = chatMessageMapper.toStreamEvent(accessSnapshot.chatId(), message);
        CompletableFuture<Boolean> redisPublish = publishChatEventAsync(streamEvent);
        List<Chat> updatedDocuments = updateChatSummaries(chatDocuments, message, userId, true);
        observeChatEventPublish(redisPublish, streamEvent);
        chatPushNotificationService.notifyRecipients(updatedDocuments, streamEvent);
        adminTelemetryService.record("chat.message.sent", "chats", accessSnapshot.chatId(), userId);
        return chatMessageMapper.toDto(message, userId);
    }


    public ChatMessageDto editMessage(String chatId, String requestedUserId, String messageId, String text) {
        String normalizedText = normalizeText(text);
        if (!StringUtils.hasText(normalizedText)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message text is required.");
        }
        return mutateMessage(chatId, requestedUserId, messageId, accessControlService::canCurrentActorEditChatMessage, (message, userId, user) -> {
            requireOwnMessage(message, userId);
            if (StringUtils.hasText(message.getDeletedAtIso())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deleted messages cannot be edited.");
            }
            message.setText(normalizedText);
            message.setEditedAtIso(Instant.now().toString());
        });
    }

    public ChatMessageDto deleteMessage(String chatId, String requestedUserId, String messageId) {
        return mutateMessage(chatId, requestedUserId, messageId, accessControlService::canCurrentActorDeleteChatMessage, (message, userId, user) -> {
            requireOwnMessage(message, userId);
            message.setText("");
            message.setDeletedAtIso(Instant.now().toString());
            message.setDeletedByUserId(userId);
            message.setDeletedByName(resolveUserName(user, userId));
            message.setReactions(new ArrayList<>());
            message.setReadBy(new ArrayList<>());
            message.setAttachments(new ArrayList<>());
            message.setReplyTo(null);
            message.setEditedAtIso(null);
            message.setPinnedAtIso(null);
            message.setPinnedByUserId(null);
        });
    }

    public ChatMessageDto pinMessage(String chatId, String requestedUserId, String messageId, Boolean pinned) {
        return mutateMessage(chatId, requestedUserId, messageId, accessControlService::canCurrentActorPinChatMessage, (message, userId, user) -> {
            if (Boolean.TRUE.equals(pinned)) {
                message.setPinnedAtIso(Instant.now().toString());
                message.setPinnedByUserId(userId);
            } else {
                message.setPinnedAtIso(null);
                message.setPinnedByUserId(null);
            }
        });
    }

    public ChatMessageDto reactToMessage(String chatId, String requestedUserId, String messageId, String emoji) {
        return mutateMessage(chatId, requestedUserId, messageId, accessControlService::canCurrentActorManageChatReaction, (message, userId, user) -> {
            String normalizedEmoji = normalizeText(emoji);
            List<ChatMessageReactionRecord> reactions = new ArrayList<>();
            for (ChatMessageReactionRecord reaction : safeReactions(message.getReactions())) {
                if (!userId.equals(normalizeId(reaction.getUserId()))) {
                    reactions.add(copyReactionRecords(List.of(reaction)).get(0));
                }
            }
            if (StringUtils.hasText(normalizedEmoji)) {
                ChatMessageReactionRecord reaction = new ChatMessageReactionRecord();
                reaction.setEmoji(normalizedEmoji);
                reaction.setUserId(userId);
                reaction.setUserName(resolveUserName(user, userId));
                reaction.setUserInitials(resolveUserInitials(user, userId));
                reaction.setUserGender(resolveUserGender(user));
                reaction.setReactedAtIso(Instant.now().toString());
                reactions.add(reaction);
            }
            message.setReactions(reactions);
        });
    }

    public ChatMessageDto updateMessageAttachments(
            String chatId,
            String requestedUserId,
            String messageId,
            List<ChatMessageAttachmentDto> attachments) {
        return mutateMessage(chatId, requestedUserId, messageId, accessControlService::canCurrentActorAddChatAttachment, (message, userId, user) -> {
            if (StringUtils.hasText(message.getDeletedAtIso())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deleted messages cannot be updated.");
            }
            message.setAttachments(normalizeAttachments(attachments));
        });
    }

    public void markMessagesRead(String chatId, String requestedUserId, List<String> messageIds) {
        String userId = resolveChatActor(chatId, requireResolvedUserId(requestedUserId));
        ChatDocumentsSnapshot snapshot = requireChat(chatId, userId);
        ChatReadUpdate readUpdate = markMessagesRead(snapshot, userId, messageIds);
        if (!readUpdate.readMessageIds().isEmpty()) {
            publishChatEvent(toReadEvent(snapshot.chatId(), readUpdate));
        }
    }

    public void sendTypingSignal(String chatId, String requestedUserId, Boolean typing) {
        String userId = resolveChatActor(chatId, requireResolvedUserId(requestedUserId));
        ChatDocumentsSnapshot snapshot = requireChat(chatId, userId);
        ProfileSummary user = findUser(userId);
        publishChatEvent(ChatStreamEvent.typing(
                snapshot.chatId(),
                userId,
                resolveUserName(user, userId),
                resolveUserInitials(user, userId),
                resolveUserGender(user),
                Boolean.TRUE.equals(typing)));
    }

    public boolean hasChatAccess(String chatId, String userId) {
        String normalizedChatId = normalizeId(chatId);
        String normalizedUserId = resolveChatActor(normalizedChatId, normalizeId(userId));
        if (!StringUtils.hasText(normalizedChatId) || !StringUtils.hasText(normalizedUserId)) {
            return false;
        }
        Chat viewerChat = chatsRepository
                .findByOwnerUserIdAndChatId(normalizedUserId, normalizedChatId)
                .orElse(null);
        if (viewerChat != null) {
            return hasChatAccess(viewerChat, normalizedUserId);
        }
        return chatsRepository.findByChatId(normalizedChatId).stream()
                .anyMatch(chat -> hasChatAccess(chat, normalizedUserId));
    }

    /** Reuse the chat's existing app-admin or resource-member management authority. */
    public boolean canModerateCall(String chatId, String userId) {
        String actor = resolveChatActor(normalizeId(chatId), normalizeId(userId));
        if (!hasChatAccess(chatId, actor)) return false;
        if (isAdminUser(findUser(actor))) return true;
        return false;
    }

    public void upsertDevice(ChatDeviceUpsertRequestDto request) {
        if (request == null) {
            return;
        }
        String userId = resolveUserId(request.userId());
        String deviceId = normalizeText(request.deviceId());
        String firebaseToken = normalizeText(request.firebaseToken());
        if (!StringUtils.hasText(userId) || (!StringUtils.hasText(deviceId) && !StringUtils.hasText(firebaseToken))) {
            return;
        }

        accountDevices.register(userId, deviceId, firebaseToken, request.platform(), request.notificationsEnabled());
    }

    public void deleteDevice(ChatDeviceDeleteRequestDto request) {
        if (request == null) {
            return;
        }
        String userId = resolveUserId(request.userId());
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String deviceId = normalizeText(request.deviceId());
        String firebaseToken = normalizeText(request.firebaseToken());
        if (!StringUtils.hasText(deviceId) && !StringUtils.hasText(firebaseToken)) {
            return;
        }

        accountDevices.remove(userId, deviceId, firebaseToken);
    }

    private ChatDocumentsSnapshot requireChat(String chatId, String userId) {
        return requireChat(chatId, userId, false);
    }

    private ChatDocumentsSnapshot requireChat(String chatId, String userId, boolean prepareOwnerCopies) {
        String normalizedChatId = normalizeId(chatId);
        if (!StringUtils.hasText(normalizedChatId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found.");
        }
        List<Chat> chatDocuments = prepareOwnerCopies
                ? ensureOwnerCopies(chatsRepository.findByChatId(normalizedChatId))
                : chatsRepository.findByChatId(normalizedChatId);
        if (chatDocuments.isEmpty() || chatDocuments.stream().noneMatch(chat -> hasChatAccess(chat, userId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found.");
        }
        Chat viewerChat = findOwnerChat(chatDocuments, userId);
        if (prepareOwnerCopies && viewerChat == null) {
            Chat baseChat = chatDocuments.get(0);
            Chat createdViewerChat = createOwnerCopy(baseChat, userId);
            chatsRepository.save(createdViewerChat);
            chatDocuments = new ArrayList<>(chatDocuments);
            chatDocuments.add(createdViewerChat);
            viewerChat = createdViewerChat;
        }
        return new ChatDocumentsSnapshot(normalizedChatId, viewerChat, chatDocuments);
    }

    private ChatDocumentsSnapshot requireChatAccessForSend(String chatId, String userId) {
        var snapshot = requireChat(chatId, userId, true);
        var chat = snapshot.viewerChat();
        if (chat != null && "contact".equals(chat.getChannelType()) && safeMemberIds(chat.getMemberIds()).size() == 2) {
            for (String peer : safeMemberIds(chat.getMemberIds())) if (!peer.equals(userId)) requireContactChatConsent(userId, peer);
        }
        return snapshot;
    }

    private void requireContactChatConsent(String actor, String target) {
        var consent = mongoTemplate.findById(ContactChatAccess.pairId(actor, target),
                ContactChatAccess.class);
        if (consent == null || !"approved".equals(consent.status()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Direct chat access requires approval.");
    }

    private List<Chat> chatDocumentsForMutation(ChatDocumentsSnapshot snapshot, String userId) {
        if (snapshot != null && snapshot.chatDocuments() != null && !snapshot.chatDocuments().isEmpty()) {
            return snapshot.chatDocuments();
        }
        return requireChatAccessForSend(snapshot == null ? "" : snapshot.chatId(), userId).chatDocuments();
    }

    private List<Chat> ensureOwnerCopies(List<Chat> chatDocuments) {
        if (chatDocuments == null || chatDocuments.isEmpty()) {
            return List.of();
        }
        Map<String, List<Chat>> chatsByChatId = groupChatsByChatId(chatDocuments);

        List<Chat> created = new ArrayList<>();
        for (List<Chat> chatsWithSameId : chatsByChatId.values()) {
            if (chatsWithSameId.isEmpty()) {
                continue;
            }
            Chat baseChat = chatsWithSameId.get(0);
            String chatId = normalizeId(baseChat.getChatId());
            if (!StringUtils.hasText(chatId)) {
                continue;
            }
            Set<String> ownerUserIds = new LinkedHashSet<>();
            Set<String> participantUserIds = new LinkedHashSet<>();
            for (Chat chat : chatsRepository.findByChatId(chatId)) {
                if (chat != null && StringUtils.hasText(chat.getOwnerUserId())) {
                    ownerUserIds.add(normalizeId(chat.getOwnerUserId()));
                }
            }
            collectChatOwnersAndParticipants(chatsWithSameId, ownerUserIds, participantUserIds);
            for (String participantUserId : participantUserIds) {
                if (ownerUserIds.contains(participantUserId)) {
                    continue;
                }
                Chat createdChat = createOwnerCopy(baseChat, participantUserId);
                created.add(createdChat);
                chatsWithSameId.add(createdChat);
                ownerUserIds.add(participantUserId);
            }
        }
        if (!created.isEmpty()) {
            chatsRepository.saveAll(created);
        }
        return flattenChatGroups(chatsByChatId);
    }

    private Map<String, List<Chat>> groupChatsByChatId(List<Chat> chatDocuments) {
        Map<String, List<Chat>> chatsByChatId = new LinkedHashMap<>();
        for (Chat chat : chatDocuments == null ? List.<Chat>of() : chatDocuments) {
            if (chat == null || !StringUtils.hasText(chat.getChatId())) {
                continue;
            }
            String chatId = normalizeId(chat.getChatId());
            chatsByChatId.computeIfAbsent(chatId, key -> new ArrayList<>()).add(chat);
        }
        return chatsByChatId;
    }

    private void collectChatOwnersAndParticipants(
            List<Chat> chatsWithSameId,
            Set<String> ownerUserIds,
            Set<String> participantUserIds) {
        for (Chat chat : chatsWithSameId == null ? List.<Chat>of() : chatsWithSameId) {
            if (StringUtils.hasText(chat.getOwnerUserId())) {
                String ownerUserId = normalizeId(chat.getOwnerUserId());
                ownerUserIds.add(ownerUserId);
                participantUserIds.add(ownerUserId);
            }
            for (String memberId : chat.getMemberIds() == null ? List.<String>of() : chat.getMemberIds()) {
                if (StringUtils.hasText(memberId)) {
                    participantUserIds.add(normalizeId(memberId));
                }
            }
        }
    }

    private List<Chat> flattenChatGroups(Map<String, List<Chat>> chatsByChatId) {
        List<Chat> flattened = new ArrayList<>();
        for (List<Chat> sameChatId : chatsByChatId.values()) {
            flattened.addAll(sameChatId);
        }
        return flattened;
    }

    private ChatReadUpdate markMessagesRead(
            ChatDocumentsSnapshot snapshot,
            String viewerUserId,
            List<String> requestedMessageIds) {
        return markMessagesRead(snapshot, viewerUserId, requestedMessageIds, null);
    }

    private ChatReadUpdate markMessagesRead(
            ChatDocumentsSnapshot snapshot,
            String viewerUserId,
            List<String> requestedMessageIds,
            List<ChatMessage> preloadedMessages) {
        Set<String> targetMessageIds = requestedMessageIds == null
                ? Set.of()
                : requestedMessageIds.stream()
                        .map(this::normalizeId)
                        .filter(StringUtils::hasText)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> readMessageIds = new LinkedHashSet<>();
        String readAtIso = Instant.now().toString();
        ProfileSummary viewer = null;
        ChatReadAvatarRecord reader = null;
        List<ChatMessage> messages = preloadedMessages != null
                ? preloadedMessages
                : chatMessagesRepository.findByChatIdAndMessageIds(snapshot.chatId(), targetMessageIds);
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            String senderId = message.getSenderAvatar() == null ? "" : normalizeId(message.getSenderAvatar().getId());
            boolean matchesTarget = targetMessageIds.contains(normalizeId(message.getId()));
            if (!matchesTarget
                    || viewerUserId.equals(senderId)
                    || StringUtils.hasText(message.getDeletedAtIso())
                    || hasReader(message, viewerUserId)) {
                continue;
            }
            if (reader == null) {
                viewer = findUser(viewerUserId);
                reader = buildReadAvatarRecord(viewer, viewerUserId);
            }
            Query unreadMessage = new Query(unreadMessageCriteria(snapshot.chatId(), viewerUserId)
                    .and("id").is(normalizeId(message.getId())));
            Update readUpdate = new Update()
                    .addToSet("readBy", reader)
                    .addToSet("messageReadByUserIds", viewerUserId);
            var updateResult = mongoTemplate.updateFirst(unreadMessage, readUpdate, ChatMessage.class);
            if (updateResult != null && updateResult.getModifiedCount() > 0) {
                addReader(message, reader);
                List<String> readByUserIds = new ArrayList<>(message.getMessageReadByUserIds());
                readByUserIds.add(viewerUserId);
                message.setMessageReadByUserIds(readByUserIds);
                readMessageIds.add(normalizeId(message.getId()));
            }
        }
        Chat viewerChat = snapshot.viewerChat();
        if (viewerChat != null && !readMessageIds.isEmpty()) {
            int unread = Math.max(0, normalizeCount(viewerChat.getUnread()) - readMessageIds.size());
            if (normalizeCount(viewerChat.getUnread()) != unread) {
                int unreadDelta = unread - normalizeCount(viewerChat.getUnread());
                Chat nextViewerChat = copyChat(viewerChat);
                nextViewerChat.setUnread(unread);
                nextViewerChat.setRevision(nextChatRevision(viewerChat));
                viewerChat = chatsRepository.save(nextViewerChat);
                incrementStoredChatCounters(nextViewerChat, unreadDelta);

            }
        }
        if (!readMessageIds.isEmpty()) {
            userNotificationService.markChatMessagesRead(viewerUserId, snapshot.chatId(), List.copyOf(readMessageIds));
        }
        return new ChatReadUpdate(
                viewerChat,
                List.copyOf(readMessageIds),
                reader == null ? chatMessageMapper.emptyReadAvatarDto() : chatMessageMapper.toReadAvatarDto(reader, viewer),
                readAtIso,
                viewerChat == null ? 0 : normalizeCount(viewerChat.getUnread()),
                !readMessageIds.isEmpty());
    }

    private ChatReadUpdate markChannelRead(
            ChatDocumentsSnapshot snapshot,
            String viewerUserId,
            List<ChatMessage> loadedMessages) {
        String readAtIso = Instant.now().toString();
        List<String> loadedUnreadIds = (loadedMessages == null ? List.<ChatMessage>of() : loadedMessages).stream()
                .filter(Objects::nonNull)
                .filter(message -> !StringUtils.hasText(message.getDeletedAtIso()))
                .filter(message -> message.getSenderAvatar() == null
                        || !viewerUserId.equals(normalizeId(message.getSenderAvatar().getId())))
                .filter(message -> !hasReader(message, viewerUserId))
                .map(ChatMessage::getId)
                .map(this::normalizeId)
                .filter(StringUtils::hasText)
                .toList();
        Chat viewerChat = snapshot.viewerChat();
        int previousUnread = viewerChat == null ? 0 : normalizeCount(viewerChat.getUnread());
        userNotificationService.markChatRead(viewerUserId, snapshot.chatId());
        if (previousUnread == 0 && loadedUnreadIds.isEmpty()) {
            return new ChatReadUpdate(
                    viewerChat,
                    List.of(),
                    chatMessageMapper.emptyReadAvatarDto(),
                    readAtIso,
                    0,
                    false);
        }
        ProfileSummary viewer = findUser(viewerUserId);
        ChatReadAvatarRecord reader = buildReadAvatarRecord(viewer, viewerUserId);
        Query unreadMessages = new Query(unreadMessageCriteria(snapshot.chatId(), viewerUserId));
        Update readUpdate = new Update()
                .addToSet("readBy", reader)
                .addToSet("messageReadByUserIds", viewerUserId);
        var updateResult = mongoTemplate.updateMulti(unreadMessages, readUpdate, ChatMessage.class);
        long changedMessageCount = updateResult.getModifiedCount();

        if (viewerChat != null && previousUnread > 0) {
            Chat nextViewerChat = copyChat(viewerChat);
            nextViewerChat.setUnread(0);
            nextViewerChat.setRevision(nextChatRevision(viewerChat));
            viewerChat = chatsRepository.save(nextViewerChat);
            incrementStoredChatCounters(nextViewerChat, -previousUnread);

        }
        boolean changed = changedMessageCount > 0 || previousUnread > 0;
        return new ChatReadUpdate(
                viewerChat,
                loadedUnreadIds,
                chatMessageMapper.toReadAvatarDto(reader, viewer),
                readAtIso,
                0,
                changed);
    }

    private Criteria unreadMessageCriteria(String chatId, String viewerUserId) {
        return new Criteria().andOperator(
                Criteria.where("chatId").is(chatId),
                Criteria.where("senderAvatar.id").ne(viewerUserId),
                Criteria.where("messageReadByUserIds").ne(viewerUserId),
                new Criteria().orOperator(
                        Criteria.where("deletedAtIso").exists(false),
                        Criteria.where("deletedAtIso").is(null),
                        Criteria.where("deletedAtIso").is("")));
    }

    private ChatMessageDto mutateMessage(
            String chatId,
            String requestedUserId,
            String messageId,
            BiPredicate<String, String> policyGuard,
            ChatMessageMutator mutator) {
        String userId = resolveChatActor(chatId, requireResolvedUserId(requestedUserId));
        String normalizedMessageId = normalizeId(messageId);
        if (!StringUtils.hasText(normalizedMessageId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message id is required.");
        }
        String normalizedChatId = normalizeId(chatId);
        if (!StringUtils.hasText(normalizedChatId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found.");
        }
        if (policyGuard != null && !policyGuard.test(userId, normalizedChatId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user is not allowed to change messages in this chat.");
        }
        ChatDocumentsSnapshot snapshot = requireChat(normalizedChatId, userId);
        ProfileSummary user = findUser(userId);
        ChatMessage message = chatMessagesRepository
                .findByChatIdAndMessageId(snapshot.chatId(), normalizedMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found."));
        mutator.apply(message, userId, user);
        message.refreshMessageSearchFields();
        ChatMessage updatedMessage = chatMessagesRepository.save(message);
        refreshChatSummaries(snapshot.chatId(), snapshot.chatDocuments());
        publishChatEvent(chatMessageMapper.toStreamEvent(snapshot.chatId(), updatedMessage));
        return chatMessageMapper.toDto(updatedMessage, userId);
    }

    private void requireOwnMessage(ChatMessage message, String userId) {
        ChatAvatarRecord senderAvatar = message == null ? null : message.getSenderAvatar();
        String senderId = senderAvatar == null ? "" : normalizeId(senderAvatar.getId());
        if (!userId.equals(senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only your own messages can be changed.");
        }
    }

    private List<Chat> updateChatSummaries(
            List<Chat> chatDocuments,
            ChatMessage message,
            String lastSenderId,
            boolean clearUnreadForSender) {
        if (chatDocuments == null || chatDocuments.isEmpty() || message == null) {
            return List.of();
        }
        List<Chat> updatedDocuments = new ArrayList<>(chatDocuments.size());
        List<ChatCounterChange> counterChanges = new ArrayList<>();
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Chat.class);
        boolean hasBulkUpdates = false;
        String summary = chatMessageMapper.summary(message);
        String sentAtIso = message.getSentAtIso();
        String normalizedLastSenderId = normalizeId(lastSenderId);
        for (Chat existing : chatDocuments) {
            if (existing == null || !StringUtils.hasText(existing.getMongoId())) {
                continue;
            }
            Chat next = copyChat(existing);
            next.setLastMessage(summary);
            next.setLastSenderId(normalizedLastSenderId);
            next.setDateIso(sentAtIso);
            boolean senderCopy = clearUnreadForSender && normalizedLastSenderId.equals(normalizeId(next.getOwnerUserId()));
            next.setUnread(senderCopy ? 0 : normalizeCount(existing.getUnread()) + 1);
            next.setRevision(nextChatRevision(existing));
            updatedDocuments.add(next);
            int unreadDelta = normalizeCount(next.getUnread()) - normalizeCount(existing.getUnread());
            if (unreadDelta != 0) {
                counterChanges.add(new ChatCounterChange(next, unreadDelta));
            }

            Update update = new Update()
                    .set("lastMessage", next.getLastMessage())
                    .set("lastSenderId", next.getLastSenderId())
                    .set("dateIso", next.getDateIso())
                    .set("unread", next.getUnread())
                    .inc("revision", 1L);
            bulk.updateOne(Query.query(Criteria.where("_id").is(existing.getMongoId())), update);
            hasBulkUpdates = true;
        }
        if (hasBulkUpdates) {
            bulk.execute();
            incrementStoredChatCounters(counterChanges);

        }
        return updatedDocuments;
    }

    private void incrementStoredChatCounters(Collection<ChatCounterChange> changes) {
        List<ChatCounterChange> applicable = changes == null
                ? List.of()
                : changes.stream()
                        .filter(Objects::nonNull)
                        .filter(change -> change.chat() != null)
                        .filter(change -> change.delta() != 0)
                        .filter(change -> StringUtils.hasText(normalizeId(change.chat().getOwnerUserId())))
                        .toList();
        if (applicable.isEmpty()) {
            return;
        }
        chatCounters.increment(applicable.stream().map(change -> new com.raxim.myscoutee.domain.accounts.api.ChatCounters.Change(
                normalizeId(change.chat().getOwnerUserId()), chatCounterContextField(change.chat().getChannelType()), change.delta())).toList());
    }

    private void incrementStoredChatCounters(Chat chat, int unreadDelta) {
        String ownerUserId = normalizeId(chat == null ? null : chat.getOwnerUserId());
        if (chat == null || !StringUtils.hasText(ownerUserId) || unreadDelta == 0) return;
        chatCounters.increment(new com.raxim.myscoutee.domain.accounts.api.ChatCounters.Change(
                ownerUserId, chatCounterContextField(chat.getChannelType()), unreadDelta));
    }

    private String chatCounterContextField(String channelType) {
        return switch (safeText(channelType)) {
            case "contact" -> "contacts";
            case CHAT_CHANNEL_APP_SUPPORT, CHAT_CHANNEL_SUPPORT_CASE -> "appSupport";
            default -> "";
        };
    }

    private void refreshChatSummaries(String chatId, List<Chat> chatDocuments) {
        String normalizedChatId = normalizeId(chatId);
        if (!StringUtils.hasText(normalizedChatId)) {
            return;
        }
        ChatMessage latest = chatMessagesRepository
                .findByChatIdOrderBySentAtIsoDesc(normalizedChatId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        List<Chat> sourceDocuments = chatDocuments == null || chatDocuments.isEmpty()
                ? chatsRepository.findByChatId(normalizedChatId)
                : chatDocuments;
        if (sourceDocuments.isEmpty()) {
            return;
        }
        List<Chat> updatedDocuments = new ArrayList<>();
        for (Chat existing : sourceDocuments) {
            if (existing == null) {
                continue;
            }
            Chat next = copyChat(existing);
            if (latest == null) {
                next.setLastMessage("");
                next.setLastSenderId("");
            } else {
                next.setLastMessage(chatMessageMapper.summary(latest));
                ChatAvatarRecord latestSender = latest.getSenderAvatar();
                next.setLastSenderId(latestSender == null ? "" : normalizeId(latestSender.getId()));
                next.setDateIso(latest.getSentAtIso());
            }
            next.setRevision(nextChatRevision(existing));
            updatedDocuments.add(next);
        }
        if (!updatedDocuments.isEmpty()) {
            chatsRepository.saveAll(updatedDocuments);
        }
    }

    @FunctionalInterface
    private interface ChatMessageMutator {
        void apply(ChatMessage message, String userId, ProfileSummary user);
    }

    private record ChatCounterChange(Chat chat, int delta) {
    }

    private boolean addReader(ChatMessage message, ChatReadAvatarRecord reader) {
        if (message == null || reader == null || !StringUtils.hasText(reader.getId())) {
            return false;
        }
        List<ChatReadAvatarRecord> readBy = new ArrayList<>(safeReadBy(message.getReadBy()));
        for (ChatReadAvatarRecord existingReader : readBy) {
            if (existingReader != null && reader.getId().equals(normalizeId(existingReader.getId()))) {
                return false;
            }
        }
        readBy.add(copyReadAvatarRecord(reader));
        message.setReadBy(readBy);
        return true;
    }

    private boolean hasReader(ChatMessage message, String viewerUserId) {
        String normalizedViewerUserId = normalizeId(viewerUserId);
        if (message == null || !StringUtils.hasText(normalizedViewerUserId)) {
            return false;
        }
        return safeReadBy(message.getReadBy()).stream()
                .map(ChatReadAvatarRecord::getId)
                .map(this::normalizeId)
                .anyMatch(normalizedViewerUserId::equals);
    }



    private ChatDto toChatDto(Chat chat) {
        return chatMapper.toDto(chat);
    }

    private ChatDto toChatDto(Chat chat, Map<String, ProfileSummary> usersById) {
        return chatMapper.toDto(chat, usersById);
    }

    private List<ChatDto> chatDtos(List<Chat> records) {
        List<Chat> safeRecords = records == null ? List.of() : records;
        Map<String, ProfileSummary> usersById = chatMemberUsers(safeRecords);
        return safeRecords.stream().map(record -> toChatDto(record, usersById))
                .filter(Objects::nonNull).toList();
    }

    private Map<String, ProfileSummary> chatMemberUsers(Collection<Chat> records) {
        Set<String> memberIds = new LinkedHashSet<>();
        for (Chat record : records == null ? List.<Chat>of() : records) {
            for (String memberId : safeMemberIds(record == null ? null : record.getMemberIds())) {
                String normalizedMemberId = normalizeId(memberId);
                if (StringUtils.hasText(normalizedMemberId)) {
                    memberIds.add(normalizedMemberId);
                }
            }
        }
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ProfileSummary> usersById = new LinkedHashMap<>();
        for (ProfileSummary user : profileDirectory.findByUserIdIn(memberIds)) {
            String userId = normalizeId(user == null ? null : user.getUserId());
            if (StringUtils.hasText(userId)) {
                usersById.put(userId, user);
            }
        }
        return usersById;
    }

    private ChatMessage buildMessage(
            ProfileSummary sender,
            String senderUserId,
            String text,
            String clientId,
            ChatMessageReplyRecord replyTo,
            List<ChatMessageAttachmentRecord> attachments) {
        Instant sentAt = Instant.now();
        ChatMessage message = new ChatMessage();
        message.setId(StringUtils.hasText(clientId)
                ? UUID.nameUUIDFromBytes((senderUserId + "\n" + clientId.trim()).getBytes(StandardCharsets.UTF_8)).toString()
                : UUID.randomUUID().toString());
        message.setClientId(normalizeText(clientId));
        message.setSender(resolveUserName(sender, senderUserId));
        message.setSenderAvatar(buildSenderAvatarRecord(sender, senderUserId));
        message.setText(text);
        message.setTime(CHAT_TIME_FORMATTER.format(sentAt));
        message.setSentAtIso(sentAt.toString());
        message.setMine(Boolean.FALSE);
        message.setReadBy(new ArrayList<>());
        message.setReplyTo(copyReplyRecord(replyTo));
        message.setReactions(new ArrayList<>());
        message.setAttachments(copyAttachmentRecords(attachments));
        return message;
    }

    private ChatMessage buildSystemMessage(String text, String systemType) {
        Instant sentAt = Instant.now();
        ChatAvatarRecord avatar = new ChatAvatarRecord();
        avatar.setId("");
        avatar.setInitials("MS");
        avatar.setGender("system");
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID().toString());
        message.setClientId(firstNonBlank(systemType, "system"));
        message.setSender("System");
        message.setSenderAvatar(avatar);
        message.setText(text);
        message.setTime(CHAT_TIME_FORMATTER.format(sentAt));
        message.setSentAtIso(sentAt.toString());
        message.setMine(Boolean.FALSE);
        message.setReadBy(new ArrayList<>());
        message.setReplyTo(null);
        message.setReactions(new ArrayList<>());
        message.setAttachments(new ArrayList<>());
        return message;
    }

    private ChatStreamEvent toReadEvent(String chatId, ChatReadUpdate readUpdate) {
        return ChatStreamEvent.read(
                chatId,
                readUpdate.reader().id(),
                readUpdate.reader().initials(),
                readUpdate.reader().gender(),
                readUpdate.reader().imageUrl(),
                readUpdate.readMessageIds(),
                readUpdate.readAtIso(),
                readUpdate.unread());
    }

    private ChatReadReceiptDto toReadReceiptDto(ChatReadUpdate readUpdate) {
        if (readUpdate == null || !readUpdate.changed()) {
            return null;
        }
        return new ChatReadReceiptDto(
                readUpdate.reader().id(),
                readUpdate.reader().initials(),
                readUpdate.reader().gender(),
                readUpdate.reader().imageUrl(),
                readUpdate.readMessageIds(),
                readUpdate.readAtIso(),
                readUpdate.unread());
    }

    private void publishChatEvent(ChatStreamEvent streamEvent) {
        chatStreamPublisher.publish(streamEvent);
    }

    private CompletableFuture<Boolean> publishChatEventAsync(ChatStreamEvent streamEvent) {
        return CompletableFuture.supplyAsync(() -> chatStreamPublisher.publish(streamEvent), chatWriteExecutor);
    }

    private void observeChatEventPublish(CompletableFuture<Boolean> redisPublish, ChatStreamEvent streamEvent) {
        if (redisPublish == null) {
            return;
        }
        redisPublish.whenComplete((published, exception) -> {
            if (exception != null) {
                LOGGER.warn("Unable to publish chat event to Redis for chat {}.", streamEvent == null ? null : streamEvent.chatId(), exception);
                return;
            }
            if (!Boolean.TRUE.equals(published)) {
                LOGGER.warn("Chat event was persisted to Mongo but not published to Redis for chat {}.", streamEvent == null ? null : streamEvent.chatId());
            }
        });
    }

    private Chat findOwnerChat(List<Chat> chats, String ownerUserId) {
        String normalizedOwnerUserId = normalizeId(ownerUserId);
        Chat selected = null;
        for (Chat chat : chats) {
            if (chat == null || !normalizedOwnerUserId.equals(normalizeId(chat.getOwnerUserId()))) {
                continue;
            }
            if (selected == null) {
                selected = chat;
                continue;
            }

            long candidateDate = sortableDate(chat.getDateIso());
            long selectedDate = sortableDate(selected.getDateIso());
            if (candidateDate > selectedDate) {
                selected = chat;
                continue;
            }
            if (candidateDate < selectedDate) {
                continue;
            }

            int candidateMessageCount = messageCount(chat);
            int selectedMessageCount = messageCount(selected);
            if (candidateMessageCount > selectedMessageCount) {
                selected = chat;
            }
        }
        return selected;
    }

    private int messageCount(Chat chat) {
        if (chat == null) {
            return 0;
        }
        int transientMessageCount = safeMessages(chat.getMessages()).size();
        if (transientMessageCount > 0) {
            return transientMessageCount;
        }
        String chatId = normalizeId(chat.getChatId());
        if (!StringUtils.hasText(chatId)) {
            return 0;
        }
        return normalizeLongCount(chatMessagesRepository.countByChatId(chatId));
    }

    private boolean hasChatAccess(Chat chat, String userId) {
        if (chat == null || !StringUtils.hasText(userId)) {
            return false;
        }
        String normalizedUserId = normalizeId(userId);
        if (normalizedUserId.equals(normalizeId(chat.getOwnerUserId()))) {
            return true;
        }
        ProfileSummary admin = isSupportCaseChat(chat) ? findUser(normalizedUserId) : null;
        if (admin != null && isSupportCaseChat(chat) && isAdminUser(admin)) {
            return Objects.equals(admin.getWorkspaceGroupId(), chat.getSupportBaseGroupId());
        }
        if (chat.getMemberIds() == null) {
            return false;
        }
        return chat.getMemberIds().stream()
                .map(this::normalizeId)
                .anyMatch(normalizedUserId::equals);
    }

    private Chat createOwnerCopy(Chat baseChat, String ownerUserId) {
        Chat copy = copyChat(baseChat);
        copy.setMongoId(buildMongoId(ownerUserId, baseChat.getChatId()));
        copy.setOwnerUserId(ownerUserId);
        copy.setUnread(countUnreadMessages(baseChat.getChatId(), ownerUserId));
        return copy;
    }

    private int countUnreadMessages(String chatId, String viewerUserId) {
        String normalizedChatId = normalizeId(chatId);
        String normalizedViewerUserId = normalizeId(viewerUserId);
        if (!StringUtils.hasText(normalizedChatId) || !StringUtils.hasText(normalizedViewerUserId)) {
            return 0;
        }
        Query unreadQuery = Query.query(Criteria.where("chatId").is(normalizedChatId)
                .and("senderAvatar.id").ne(normalizedViewerUserId)
                .and("readBy.id").ne(normalizedViewerUserId));
        return normalizeLongCount(mongoTemplate.count(unreadQuery, ChatMessage.class));
    }

    private Chat copyChat(Chat source) {
        Chat copy = new Chat();
        copy.setMongoId(source.getMongoId());
        copy.setChatId(source.getChatId());
        copy.setOwnerUserId(source.getOwnerUserId());
        copy.setAvatar(source.getAvatar());
        copy.setTitle(source.getTitle());
        copy.setLastMessage(source.getLastMessage());
        copy.setLastSenderId(source.getLastSenderId());
        copy.setMemberIds(new ArrayList<>(source.getMemberIds() == null ? List.of() : source.getMemberIds()));
        copy.setUnread(source.getUnread());
        copy.setDateIso(source.getDateIso());
        copy.setContextStartAtIso(source.getContextStartAtIso());
        copy.setContextEndAtIso(source.getContextEndAtIso());
        copy.setChannelType(source.getChannelType());
        copy.setOwnerId(source.getOwnerId());
        copy.setEventId(source.getEventId());
        copy.setOwnerStatus(source.getOwnerStatus());
        copy.setRevision(normalizeChatRevision(source.getRevision()));
        copy.setSubEventId(source.getSubEventId());
        copy.setGroupId(source.getGroupId());
        copy.setDistanceKm(null);
        copy.setDistanceMetersExact(null);
        copy.setSupportCase(copySupportCase(source.getSupportCase()));
        copy.setSupportBaseGroupId(source.getSupportBaseGroupId());
        return copy;
    }

    private long normalizeChatRevision(Long value) {
        return value == null || value < 1L ? 1L : value;
    }

    private long nextChatRevision(Chat chat) {
        long current = normalizeChatRevision(chat == null ? null : chat.getRevision());
        return current + 1L;
    }

    private ChatMessageReplyRecord normalizeReply(ChatMessageReplyDto replyTo) {
        if (replyTo == null || !StringUtils.hasText(replyTo.id())) {
            return null;
        }
        ChatMessageReplyRecord record = new ChatMessageReplyRecord();
        record.setId(safeText(replyTo.id()));
        record.setSender(safeText(replyTo.sender()));
        record.setText(safeText(replyTo.text()));
        return record;
    }

    private ChatMessageReplyRecord copyReplyRecord(ChatMessageReplyRecord source) {
        if (source == null) {
            return null;
        }
        ChatMessageReplyRecord copy = new ChatMessageReplyRecord();
        copy.setId(source.getId());
        copy.setSender(source.getSender());
        copy.setText(source.getText());
        return copy;
    }

    private List<ChatMessageAttachmentRecord> normalizeAttachments(List<ChatMessageAttachmentDto> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        List<ChatMessageAttachmentRecord> normalized = new ArrayList<>();
        for (ChatMessageAttachmentDto attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String type = safeText(attachment.type());
            String title = safeText(attachment.title());
            String url = safeText(attachment.url());
            String previewUrl = safeText(attachment.previewUrl());
            if (!StringUtils.hasText(type) || (!StringUtils.hasText(title) && !StringUtils.hasText(url) && !StringUtils.hasText(previewUrl))) {
                continue;
            }
            ChatMessageAttachmentRecord record = new ChatMessageAttachmentRecord();
            record.setId(StringUtils.hasText(attachment.id()) ? safeText(attachment.id()) : UUID.randomUUID().toString());
            record.setType(type);
            record.setTitle(title);
            record.setEntityId(safeText(attachment.entityId()));
            record.setAssetType(safeText(attachment.assetType()));
            record.setOwnerUserId(safeText(attachment.ownerUserId()));
            record.setStatus(normalizeAttachmentStatus(attachment.status()));
            record.setUnavailableReason(safeText(attachment.unavailableReason()));
            record.setSubtitle(safeText(attachment.subtitle()));
            record.setDescription(safeText(attachment.description()));
            record.setUrl(url);
            record.setPreviewUrl(previewUrl);
            record.setMimeType(safeText(attachment.mimeType()));
            record.setSizeBytes(attachment.sizeBytes() == null ? null : Math.max(0L, attachment.sizeBytes()));
            normalized.add(record);
        }
        canonicalizePollVotePresentations(normalized);
        return normalized;
    }

    private void canonicalizePollVotePresentations(List<ChatMessageAttachmentRecord> attachments) {
        Map<ChatMessageAttachmentRecord, Document> pollDocuments = new LinkedHashMap<>();
        Set<String> voterUserIds = new LinkedHashSet<>();
        for (ChatMessageAttachmentRecord attachment : safeAttachments(attachments)) {
            if (attachment == null || !"poll".equalsIgnoreCase(safeText(attachment.getType()))) {
                continue;
            }
            try {
                Document root = Document.parse(safeText(attachment.getDescription()));
                Object optionsValue = root.get("options");
                if (!(optionsValue instanceof List<?> optionNodes)) {
                    continue;
                }
                pollDocuments.put(attachment, root);
                for (Object optionNode : optionNodes) {
                    if (!(optionNode instanceof Document optionDocument)) {
                        continue;
                    }
                    Object votesValue = optionDocument.get("votes");
                    if (!(votesValue instanceof List<?> voteNodes)) {
                        continue;
                    }
                    for (Object voteNode : voteNodes) {
                        if (voteNode instanceof Document voteDocument) {
                            String voterUserId = normalizeId(Objects.toString(voteDocument.get("userId"), ""));
                            if (StringUtils.hasText(voterUserId)) {
                                voterUserIds.add(voterUserId);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Preserve malformed legacy poll payloads; normal attachment validation remains authoritative.
            }
        }
        if (pollDocuments.isEmpty() || voterUserIds.isEmpty()) {
            return;
        }
        Map<String, ProfileSummary> usersById = new LinkedHashMap<>();
        for (ProfileSummary user : profileDirectory.findByUserIdIn(voterUserIds)) {
            String userId = user == null ? "" : normalizeId(user.getUserId());
            if (StringUtils.hasText(userId)) {
                usersById.put(userId, user);
            }
        }
        for (Map.Entry<ChatMessageAttachmentRecord, Document> entry : pollDocuments.entrySet()) {
            Object optionsValue = entry.getValue().get("options");
            if (!(optionsValue instanceof List<?> optionNodes)) {
                continue;
            }
            for (Object optionNode : optionNodes) {
                if (!(optionNode instanceof Document optionDocument)) {
                    continue;
                }
                Object votesValue = optionDocument.get("votes");
                if (!(votesValue instanceof List<?> voteNodes)) {
                    continue;
                }
                for (Object voteNode : voteNodes) {
                    if (!(voteNode instanceof Document voteDocument)) {
                        continue;
                    }
                    String voterUserId = normalizeId(Objects.toString(voteDocument.get("userId"), ""));
                    if (!StringUtils.hasText(voterUserId)) {
                        continue;
                    }
                    ProfileSummary voter = usersById.get(voterUserId);
                    boolean deleted = voter == null
                            || "D".equalsIgnoreCase(safeText(voter.getStatus()))
                            || "deleted".equalsIgnoreCase(safeText(voter.getProfileStatus()));
                    voteDocument.put("name", deleted ? "Deleted user" : resolveUserName(voter, voterUserId));
                    voteDocument.put("initials", deleted ? "DU" : resolveUserInitials(voter, voterUserId));
                    voteDocument.put("gender", deleted ? "deleted" : resolveUserGender(voter));
                    voteDocument.put("imageUrl", deleted ? "" : resolveUserImageUrl(voter));
                }
            }
            entry.getKey().setDescription(entry.getValue().toJson());
        }
    }

    private List<ChatMessageAttachmentRecord> copyAttachmentRecords(List<ChatMessageAttachmentRecord> attachments) {
        List<ChatMessageAttachmentRecord> copied = new ArrayList<>();
        for (ChatMessageAttachmentRecord attachment : safeAttachments(attachments)) {
            ChatMessageAttachmentRecord copy = new ChatMessageAttachmentRecord();
            copy.setId(attachment.getId());
            copy.setType(attachment.getType());
            copy.setTitle(attachment.getTitle());
            copy.setEntityId(attachment.getEntityId());
            copy.setAssetType(attachment.getAssetType());
            copy.setOwnerUserId(attachment.getOwnerUserId());
            copy.setStatus(normalizeAttachmentStatus(attachment.getStatus()));
            copy.setUnavailableReason(attachment.getUnavailableReason());
            copy.setLifecycleRevision(attachment.getLifecycleRevision());
            copy.setSubtitle(attachment.getSubtitle());
            copy.setDescription(attachment.getDescription());
            copy.setUrl(attachment.getUrl());
            copy.setPreviewUrl(attachment.getPreviewUrl());
            copy.setMimeType(attachment.getMimeType());
            copy.setSizeBytes(attachment.getSizeBytes());
            copied.add(copy);
        }
        return copied;
    }

    private List<ChatMessageReactionRecord> copyReactionRecords(List<ChatMessageReactionRecord> reactions) {
        List<ChatMessageReactionRecord> copied = new ArrayList<>();
        for (ChatMessageReactionRecord reaction : safeReactions(reactions)) {
            ChatMessageReactionRecord copy = new ChatMessageReactionRecord();
            copy.setEmoji(reaction.getEmoji());
            copy.setUserId(reaction.getUserId());
            copy.setUserName(reaction.getUserName());
            copy.setUserInitials(reaction.getUserInitials());
            copy.setUserGender(reaction.getUserGender());
            copy.setReactedAtIso(reaction.getReactedAtIso());
            copied.add(copy);
        }
        return copied;
    }

    private ChatAvatarRecord buildSenderAvatarRecord(ProfileSummary sender, String senderUserId) {
        ChatAvatarRecord avatar = new ChatAvatarRecord();
        avatar.setId(senderUserId);
        avatar.setInitials(resolveUserInitials(sender, senderUserId));
        avatar.setGender(resolveUserGender(sender));
        return avatar;
    }

    private ChatAvatarRecord copySenderAvatarRecord(ChatAvatarRecord source) {
        if (source == null) {
            return null;
        }
        ChatAvatarRecord copy = new ChatAvatarRecord();
        copy.setId(source.getId());
        copy.setInitials(source.getInitials());
        copy.setGender(source.getGender());
        return copy;
    }

    private ChatReadAvatarRecord buildReadAvatarRecord(ProfileSummary user, String userId) {
        ChatReadAvatarRecord record = new ChatReadAvatarRecord();
        record.setId(userId);
        record.setInitials(resolveUserInitials(user, userId));
        record.setGender(resolveUserGender(user));
        return record;
    }

    private ChatReadAvatarRecord copyReadAvatarRecord(ChatReadAvatarRecord source) {
        if (source == null) {
            return null;
        }
        ChatReadAvatarRecord copy = new ChatReadAvatarRecord();
        copy.setId(source.getId());
        copy.setInitials(source.getInitials());
        copy.setGender(source.getGender());
        return copy;
    }

    private ProfileSummary findUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return profileDirectory.findByUserId(userId).orElse(null);
    }

    private String accountUserId(String profileId) {
        ProfileSummary profile = findUser(profileId);
        return profile != null && StringUtils.hasText(profile.getAccountUserId())
                ? profile.getAccountUserId() : profileId;
    }

    private String resolveChatActor(String chatId, String profileId) {
        return accessControlService.resolveChatActorUserId(chatId, profileId);
    }

    private String requireResolvedUserId(String requestedUserId) {
        String userId = resolveUserId(requestedUserId);
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user.");
        }
        return userId;
    }

    private String resolveUserId(String requestedUserId) {
        String resolvedUserId = normalizeId(selectedProfile.resolveSelectedUserId(requestedUserId));
        if (StringUtils.hasText(resolvedUserId)) {
            return resolvedUserId;
        }
        String normalizedRequestedUserId = normalizeId(requestedUserId);
        if (MongoSessionModeContext.currentMode() == MongoSessionMode.DEMO
                && normalizedRequestedUserId.startsWith("admin-demo-")) {
            return normalizedRequestedUserId;
        }
        return "";
    }

    private String resolveUserName(ProfileSummary user, String fallbackUserId) {
        if (user != null && StringUtils.hasText(user.getName())) {
            return user.getName().trim();
        }
        return StringUtils.hasText(fallbackUserId) ? fallbackUserId.trim() : "User";
    }

    private String resolveUserInitials(ProfileSummary user, String fallbackUserId) {
        if (user != null && StringUtils.hasText(user.getInitials())) {
            return user.getInitials().trim();
        }
        if (user != null && StringUtils.hasText(user.getName())) {
            return buildInitials(user.getName());
        }
        return buildInitials(fallbackUserId);
    }

    private String resolveUserGender(ProfileSummary user) {
        return normalizeGender(user == null ? null : user.getGender());
    }

    private String resolveUserImageUrl(ProfileSummary user) {
        return com.raxim.myscoutee.domain.chat.mapper.ChatMessageMapper.resolveUserImageUrl(user);
    }

    private String buildInitials(String value) {
        return com.raxim.myscoutee.domain.chat.mapper.ChatMessageMapper.buildInitials(value);
    }

    private String normalizeGender(String value) {
        String normalized = safeText(value).toLowerCase(Locale.US);
        if ("deleted".equals(normalized)) {
            return "deleted";
        }
        if (normalized.startsWith("w") || normalized.startsWith("f")) {
            return "woman";
        }
        return "man";
    }

    private String normalizeAttachmentStatus(String value) {
        return "unavailable".equals(safeText(value)) ? "unavailable" : "available";
    }

    private List<? extends ChatMessage> safeMessages(List<? extends ChatMessage> messages) {
        return messages == null ? List.of() : messages;
    }

    private List<String> safeMemberIds(List<String> memberIds) {
        return memberIds == null ? List.of() : memberIds;
    }

    private List<ChatReadAvatarRecord> safeReadBy(List<ChatReadAvatarRecord> readBy) {
        return readBy == null ? List.of() : readBy;
    }

    private List<ChatMessageAttachmentRecord> safeAttachments(List<ChatMessageAttachmentRecord> attachments) {
        return attachments == null ? List.of() : attachments;
    }

    private List<ChatMessageReactionRecord> safeReactions(List<ChatMessageReactionRecord> reactions) {
        return reactions == null ? List.of() : reactions;
    }

    private int normalizeCount(Integer value) {
        return Math.max(0, value == null ? 0 : value);
    }

    private int normalizeLongCount(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private int normalizeChatMessagesPageSize(Integer value) {
        if (value == null) {
            return DEFAULT_CHAT_MESSAGES_PAGE_SIZE;
        }
        return Math.max(1, Math.min(MAX_CHAT_MESSAGES_PAGE_SIZE, value));
    }

    private int normalizeChatMessagesCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
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

    private int countValue(Integer value) {
        return normalizeCount(value);
    }

    private String normalizeChatContextFilter(String value) {
        String normalized = safeText(value);
        if ("service".equalsIgnoreCase(normalized)
                || "contacts".equalsIgnoreCase(normalized)
                || "appSupport".equalsIgnoreCase(normalized)) {
            return normalized;
        }
        return "all";
    }

    private boolean isSupportCaseChat(Chat chat) {
        if (chat == null) {
            return false;
        }
        return "supportCase".equalsIgnoreCase(safeText(chat.getChannelType()))
                || StringUtils.hasText(supportStatus(chat))
                || normalizeId(chat.getChatId()).startsWith("c-support-admin-");
    }

    private boolean isAdminUser(ProfileSummary user) {
        return adminMenuCounterService.isAdminUser(user);
    }


    private SupportCaseRecord copySupportCase(SupportCaseRecord source) {
        if (source == null) {
            return null;
        }
        SupportCaseRecord copy = new SupportCaseRecord();
        copy.setStatus(source.getStatus());
        copy.setUpdatedAtIso(source.getUpdatedAtIso());
        SupportCaseAssigneeRecord sourceAssignee = source.getAssignee();
        if (sourceAssignee != null) {
            SupportCaseAssigneeRecord assignee = new SupportCaseAssigneeRecord();
            assignee.setUserId(sourceAssignee.getUserId());
            assignee.setName(sourceAssignee.getName());
            assignee.setInitials(sourceAssignee.getInitials());
            copy.setAssignee(assignee);
        }
        return copy;
    }

    private String supportStatus(Chat chat) {
        SupportCaseRecord supportCase = chat == null ? null : chat.getSupportCase();
        return safeText(supportCase == null ? null : supportCase.getStatus());
    }

    private String normalizeSupportCaseStatus(String value) {
        String normalized = safeText(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case SUPPORT_CASE_STATUS_WARNED, SUPPORT_CASE_STATUS_PICKED, SUPPORT_CASE_STATUS_SOLVED, SUPPORT_CASE_STATUS_BLOCKED -> normalized;
            default -> SUPPORT_CASE_STATUS_PENDING;
        };
    }

    private String normalizeSupportCaseFilter(String value) {
        String normalized = safeText(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case SUPPORT_CASE_STATUS_PENDING, SUPPORT_CASE_STATUS_WARNED, SUPPORT_CASE_STATUS_PICKED, SUPPORT_CASE_STATUS_SOLVED, SUPPORT_CASE_STATUS_BLOCKED -> normalized;
            default -> SUPPORT_CASE_FILTER_ALL;
        };
    }





    private String normalizeChatQuerySort(String sortValue, String secondaryFilterValue) {
        String normalizedSort = safeText(sortValue).toLowerCase(Locale.ROOT);
        if ("relevance".equals(normalizedSort)) {
            return normalizedSort;
        }
        if ("relevant".equalsIgnoreCase(safeText(secondaryFilterValue))) {
            return "relevance";
        }
        return "date";
    }

    private boolean resolveChatSortAscending(String sort, String sortDirectionValue) {
        String normalizedDirection = safeText(sortDirectionValue);
        if ("asc".equalsIgnoreCase(normalizedDirection)) {
            return true;
        }
        if ("desc".equalsIgnoreCase(normalizedDirection)) {
            return false;
        }
        return false;
    }

    private int normalizeChatPageLimit(Integer limitValue) {
        if (limitValue == null || limitValue.intValue() <= 0) {
            return 10;
        }
        return Math.min(100, limitValue.intValue());
    }

    private Criteria chatPageCriteria(
            String userId,
            String contextFilter,
            String rangeStartIso,
            String rangeEndIso,
            boolean supportCasesOnly,
            String supportCaseFilter) {
        List<Criteria> criteria = new ArrayList<>();
        criteria.add(Criteria.where("ownerUserId").is(normalizeId(userId)));
        String normalizedContext = normalizeChatContextFilter(contextFilter);
        if ("contacts".equals(normalizedContext)) {
            criteria.add(Criteria.where("channelType").is("contact"));
        } else if ("appSupport".equals(normalizedContext) || "service".equals(normalizedContext)) {
            criteria.add(Criteria.where("channelType").in(CHAT_CHANNEL_APP_SUPPORT, CHAT_CHANNEL_SUPPORT_CASE));
        }
        if (supportCasesOnly) {
            criteria.add(Criteria.where("supportCase.status").exists(true));
            String normalizedSupportFilter = normalizeSupportCaseFilter(supportCaseFilter);
            if (!SUPPORT_CASE_FILTER_ALL.equals(normalizedSupportFilter)) {
                criteria.add(Criteria.where("supportCase.status").is(normalizedSupportFilter));
            }
        }
        String normalizedRangeStart = safeText(rangeStartIso);
        String normalizedRangeEnd = safeText(rangeEndIso);
        if (StringUtils.hasText(normalizedRangeStart) || StringUtils.hasText(normalizedRangeEnd)) {
            Criteria dateCriteria = Criteria.where("dateIso");
            if (StringUtils.hasText(normalizedRangeStart)) {
                dateCriteria = dateCriteria.gte(normalizedRangeStart);
            }
            if (StringUtils.hasText(normalizedRangeEnd)) {
                dateCriteria = dateCriteria.lte(normalizedRangeEnd);
            }
            criteria.add(dateCriteria);
        }
        return new Criteria().andOperator(criteria.toArray(Criteria[]::new));
    }

    private Sort chatPageSort(String sort, boolean ascending) {
        if ("relevance".equals(sort)) {
            Sort.Direction relevanceDirection = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
            return Sort.by(
                    new Sort.Order(relevanceDirection, "unread"),
                    new Sort.Order(Sort.Direction.DESC, "dateIso"),
                    new Sort.Order(Sort.Direction.ASC, "chatId"));
        }
        Sort.Direction dateDirection = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(
                new Sort.Order(dateDirection, "dateIso"),
                new Sort.Order(Sort.Direction.ASC, "chatId"));
    }

    private Document chatPageSortDocument(String sort, boolean ascending) {
        if ("relevance".equals(sort)) {
            return new Document()
                    .append("unread", ascending ? 1 : -1)
                    .append("dateIso", -1)
                    .append("id", 1);
        }
        return new Document()
                .append("dateIso", ascending ? 1 : -1)
                .append("id", 1);
    }

    private Document supportCaseSelectionMatchDocument(
            long rangeStartMs,
            long rangeEndMs,
            String supportCaseFilter) {
        List<Document> criteria = new ArrayList<>();
        String normalizedSupportFilter = normalizeSupportCaseFilter(supportCaseFilter);
        if (!SUPPORT_CASE_FILTER_ALL.equals(normalizedSupportFilter)) {
            criteria.add(new Document("supportCase.status", normalizedSupportFilter));
        }
        Document dateRange = new Document();
        String rangeStartIso = rangeStartIsoFromMillis(rangeStartMs);
        String rangeEndIso = rangeEndIsoFromMillis(rangeEndMs);
        if (StringUtils.hasText(rangeStartIso)) {
            dateRange.append("$gte", rangeStartIso);
        }
        if (StringUtils.hasText(rangeEndIso)) {
            dateRange.append("$lte", rangeEndIso);
        }
        if (!dateRange.isEmpty()) {
            criteria.add(new Document("dateIso", dateRange));
        }
        if (criteria.isEmpty()) {
            return new Document();
        }
        return criteria.size() == 1
                ? criteria.get(0)
                : new Document("$and", criteria);
    }

    private List<Document> aggregationDocuments(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .toList();
    }

    private long aggregationNumber(Object value) {
        return value instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private int parseChatCursorOffset(String cursor) {
        try {
            return Math.max(0, Integer.parseInt(safeText(cursor)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int safePageTotal(long total) {
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) total);
    }

    private String rangeStartIsoFromMillis(long value) {
        return value == Long.MIN_VALUE ? null : Instant.ofEpochMilli(value).toString();
    }

    private String rangeEndIsoFromMillis(long value) {
        return value == Long.MAX_VALUE ? null : Instant.ofEpochMilli(value).toString();
    }

    private long sortableDate(String value) {
        String normalized = safeText(value);
        if (!StringUtils.hasText(normalized)) {
            return 0L;
        }
        try {
            return Instant.parse(normalized).toEpochMilli();
        } catch (Exception exception) {
            // Some Mongo seed dates are stored as ISO local date-times without a zone.
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant().toEpochMilli();
        } catch (Exception exception) {
            // Fall through to local date-time parsing.
        }
        try {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();
        } catch (Exception exception) {
            return 0L;
        }
    }

    private long parseIsoMillis(String value, long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Instant.parse(value.trim()).toEpochMilli();
        } catch (Exception exception) {
            return fallback;
        }
    }



    private String buildMongoId(String ownerUserId, String chatId) {
        String stableKey = normalizeId(ownerUserId) + "|" + normalizeId(chatId);
        return "chat-owner-copy:" + UUID.nameUUIDFromBytes(stableKey.getBytes(StandardCharsets.UTF_8));
    }

    private record InitialSystemMessageResult(ChatMessageDto message, boolean inserted) {
    }

    private record ChatReadUpdate(
            Chat viewerChat,
            List<String> readMessageIds,
            ChatReadAvatarDto reader,
            String readAtIso,
            Integer unread,
            boolean changed) {
    }

    private record ChatDocumentsSnapshot(String chatId, Chat viewerChat, List<Chat> chatDocuments) {
    }


}
