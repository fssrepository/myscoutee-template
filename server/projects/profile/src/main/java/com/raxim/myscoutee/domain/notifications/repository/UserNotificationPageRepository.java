package com.raxim.myscoutee.domain.notifications.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.mongodb.client.result.UpdateResult;
import com.raxim.myscoutee.domain.notifications.document.UserNotification;

@Repository
public class UserNotificationPageRepository {
    private static final Sort NOTIFICATION_SORT = Sort.by(
            Sort.Order.desc("createdDate"),
            Sort.Order.desc("_id"));

    private final MongoTemplate mongoTemplate;

    public UserNotificationPageRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public UserNotificationSlice findPage(
            String recipientUserId,
            boolean unreadOnly,
            int limit,
            UserNotificationCursor cursor) { return findPage(recipientUserId, unreadOnly, limit, cursor, null); }

    public UserNotificationSlice findPage(String recipientUserId, boolean unreadOnly, int limit, UserNotificationCursor cursor, String workspace) {
        Criteria baseCriteria = workspaceCriteria(recipientCriteria(recipientUserId, unreadOnly), workspace);
        long total = mongoTemplate.count(Query.query(baseCriteria), UserNotification.class);
        if (total == 0L) {
            return new UserNotificationSlice(List.of(), 0L, false);
        }

        Query query = Query.query(workspaceCriteria(pageCriteria(recipientUserId, unreadOnly, cursor), workspace))
                .with(NOTIFICATION_SORT)
                .limit(limit + 1);
        List<UserNotification> matches = mongoTemplate.find(query, UserNotification.class);
        boolean hasNext = matches.size() > limit;
        List<UserNotification> records = hasNext
                ? List.copyOf(matches.subList(0, limit))
                : List.copyOf(matches);
        return new UserNotificationSlice(records, total, hasNext);
    }

    public long countUnread(String recipientUserId) {
        return mongoTemplate.count(
                Query.query(recipientCriteria(recipientUserId, true)),
                UserNotification.class);
    }

    public List<UserNotification> findByRecipientAndIds(
            String recipientUserId,
            List<String> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return List.of();
        }
        return mongoTemplate.find(
                Query.query(new Criteria().andOperator(
                        Criteria.where("recipientUserId").is(recipientUserId),
                        Criteria.where("_id").in(notificationIds))),
                UserNotification.class);
    }

    public List<UserNotification> findLoadedWindow(
            String recipientUserId,
            boolean unreadOnly,
            String tailCreatedDate,
            String tailId,
            int limit) { return findLoadedWindow(recipientUserId, unreadOnly, tailCreatedDate, tailId, limit, null); }

    public List<UserNotification> findLoadedWindow(String recipientUserId, boolean unreadOnly,
            String tailCreatedDate, String tailId, int limit, String workspace) {
        boolean hasTail = tailCreatedDate != null && !tailCreatedDate.isBlank()
                && tailId != null && !tailId.isBlank();
        Criteria criteria = workspaceCriteria(recipientCriteria(recipientUserId, unreadOnly), workspace);
        if (hasTail) {
            Criteria withinLoadedWindow = new Criteria().orOperator(
                    Criteria.where("createdDate").gt(tailCreatedDate),
                    new Criteria().andOperator(
                            Criteria.where("createdDate").is(tailCreatedDate),
                            Criteria.where("_id").gte(tailId)));
            criteria = new Criteria().andOperator(criteria, withinLoadedWindow);
        }
        return mongoTemplate.find(
                Query.query(criteria)
                        .with(NOTIFICATION_SORT)
                        .limit(Math.max(1, limit)),
                UserNotification.class);
    }

    public boolean insertIfAbsent(UserNotification notification) {
        if (notification == null) {
            return false;
        }
        try {
            mongoTemplate.insert(notification);
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public AggregatedNotificationUpsert upsertAggregated(UserNotification notification) {
        if (notification == null || notification.getAggregationKey() == null
                || notification.getAggregationKey().isBlank()) {
            return new AggregatedNotificationUpsert(false, false);
        }
        Criteria criteria = Criteria.where("aggregationKey").is(notification.getAggregationKey());
        initializeMissingRevision(criteria);
        Update update = new Update()
                .setOnInsert("_id", notification.getId())
                .setOnInsert("syncKey", notification.getSyncKey())
                .setOnInsert("aggregationKey", notification.getAggregationKey())
                .setOnInsert("recipientUserId", notification.getRecipientUserId())
                .set("outboxId", notification.getOutboxId())
                .set("kind", notification.getKind())
                .set("category", notification.getCategory())
                .set("title", notification.getTitle())
                .set("message", notification.getMessage())
                .set("senderUserId", notification.getSenderUserId())
                .set("actionPath", notification.getActionPath())
                .set("sourceType", notification.getSourceType())
                .set("sourceId", notification.getSourceId())
                .set("payload", notification.getPayload())
                .set("createdDate", notification.getCreatedDate())
                .set("createdUser", notification.getCreatedUser())
                .set("updatedDate", notification.getUpdatedDate())
                .set("updatedUser", notification.getUpdatedUser())
                .unset("readAtIso")
                .inc("occurrenceCount", 1)
                .inc("revision", 1L);
        UserNotification previous = mongoTemplate.findAndModify(
                Query.query(criteria),
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(false),
                UserNotification.class);
        boolean inserted = previous == null;
        boolean becameUnread = inserted
                || (previous.getReadAtIso() != null && !previous.getReadAtIso().isBlank());
        return new AggregatedNotificationUpsert(inserted, becameUnread);
    }

    public Optional<UserNotification> markRead(
            String notificationId,
            String recipientUserId,
            String readAtIso) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("_id").is(notificationId),
                Criteria.where("recipientUserId").is(recipientUserId),
                unreadCriteria());
        initializeMissingRevision(criteria);
        UserNotification updated = mongoTemplate.findAndModify(
                Query.query(criteria),
                new Update()
                        .set("readAtIso", readAtIso)
                        .set("updatedDate", readAtIso)
                        .set("updatedUser", recipientUserId)
                        .inc("revision", 1L),
                FindAndModifyOptions.options().returnNew(true),
                UserNotification.class);
        return Optional.ofNullable(updated);
    }

    public long markUnreadBySource(
            String recipientUserId,
            String kind,
            String sourceType,
            String sourceId,
            String readAtIso) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("recipientUserId").is(recipientUserId),
                Criteria.where("kind").is(kind),
                Criteria.where("sourceType").is(sourceType),
                Criteria.where("sourceId").is(sourceId),
                unreadCriteria());
        initializeMissingRevision(criteria);
        UpdateResult result = mongoTemplate.updateMulti(
                Query.query(criteria),
                new Update()
                        .set("readAtIso", readAtIso)
                        .set("updatedDate", readAtIso)
                        .set("updatedUser", recipientUserId)
                        .inc("revision", 1L),
                UserNotification.class);
        return result.getModifiedCount();
    }

    public long markUnreadChatMessagesRead(
            String recipientUserId,
            String chatId,
            java.util.Collection<String> messageIds,
            String readAtIso) {
        if (messageIds == null || messageIds.isEmpty()) {
            return 0L;
        }
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("recipientUserId").is(recipientUserId),
                Criteria.where("kind").is("chat-message"),
                Criteria.where("sourceType").is("chat"),
                Criteria.where("sourceId").is(chatId),
                Criteria.where("payload.messageId").in(messageIds),
                unreadCriteria());
        initializeMissingRevision(criteria);
        UpdateResult result = mongoTemplate.updateMulti(
                Query.query(criteria),
                new Update()
                        .set("readAtIso", readAtIso)
                        .set("updatedDate", readAtIso)
                        .set("updatedUser", recipientUserId)
                        .inc("revision", 1L),
                UserNotification.class);
        return result.getModifiedCount();
    }

    private Criteria pageCriteria(
            String recipientUserId,
            boolean unreadOnly,
            UserNotificationCursor cursor) {
        Criteria baseCriteria = recipientCriteria(recipientUserId, unreadOnly);
        if (cursor == null) {
            return baseCriteria;
        }
        Criteria afterCursor = new Criteria().orOperator(
                Criteria.where("createdDate").lt(cursor.createdDate()),
                new Criteria().andOperator(
                        Criteria.where("createdDate").is(cursor.createdDate()),
                        Criteria.where("_id").lt(cursor.id())));
        return new Criteria().andOperator(baseCriteria, afterCursor);
    }

    private Criteria recipientCriteria(String recipientUserId, boolean unreadOnly) {
        if (!unreadOnly) {
            return Criteria.where("recipientUserId").is(recipientUserId);
        }
        return new Criteria().andOperator(
                Criteria.where("recipientUserId").is(recipientUserId),
                unreadCriteria());
    }

    private Criteria workspaceCriteria(Criteria base, String workspace) {
        if (workspace == null || workspace.isBlank() || "all".equals(workspace)) return base;
        return new Criteria().andOperator(base, Criteria.where("payload.workspaceGroupId").is("main".equals(workspace) ? null : workspace));
    }

    private Criteria unreadCriteria() {
        return new Criteria().orOperator(
                Criteria.where("readAtIso").exists(false),
                Criteria.where("readAtIso").is(null));
    }

    private void initializeMissingRevision(Criteria criteria) {
        mongoTemplate.updateMulti(
                Query.query(new Criteria().andOperator(
                        criteria,
                        new Criteria().orOperator(
                                Criteria.where("revision").exists(false),
                                Criteria.where("revision").is(null)))),
                new Update().set("revision", 1L),
                UserNotification.class);
    }

    public record UserNotificationCursor(
            String bucket,
            String createdDate,
            String id) {
    }

    public record UserNotificationSlice(
            List<UserNotification> records,
            long total,
            boolean hasNext) {
    }

    public record AggregatedNotificationUpsert(
            boolean inserted,
            boolean becameUnread) {
    }
}
