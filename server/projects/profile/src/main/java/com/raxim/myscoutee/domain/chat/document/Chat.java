package com.raxim.myscoutee.domain.chat.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "chats")
@CompoundIndexes({
        @CompoundIndex(name = "chat_owner_public_id", def = "{'ownerUserId': 1, 'id': 1}", unique = true),
        @CompoundIndex(name = "chat_public_id_lookup", def = "{'id': 1}"),
        @CompoundIndex(name = "chat_owner_date", def = "{'ownerUserId': 1, 'dateIso': -1}"),
        @CompoundIndex(name = "chat_owner_channel_owner", def = "{'ownerUserId': 1, 'channelType': 1, 'ownerId': 1}"),
        @CompoundIndex(name = "chat_owner_channel_date", def = "{'ownerUserId': 1, 'channelType': 1, 'dateIso': -1, 'id': 1}"),
        @CompoundIndex(name = "chat_owner_channel_relevance", def = "{'ownerUserId': 1, 'channelType': 1, 'unread': -1, 'dateIso': -1, 'id': 1}"),
        @CompoundIndex(name = "chat_owner_support_status_date", def = "{'ownerUserId': 1, 'supportCase.status': 1, 'dateIso': -1, 'id': 1}")
})
public class Chat implements com.raxim.myscoutee.domain.chat.api.ChatDirectory.Chat {
    @MongoId(FieldType.STRING)
    private String mongoId;

    @Field("id")
    private String chatId;

    @Indexed(name = "chat_owner_user")
    private String ownerUserId;
    private String supportBaseGroupId;
    public String getSupportBaseGroupId() { return supportBaseGroupId; }
    public void setSupportBaseGroupId(String value) { supportBaseGroupId = value; }

    private String avatar;
    private String title;
    private String lastMessage;
    private String lastSenderId;
    private List<String> memberIds = new ArrayList<>();
    private Integer unread;
    private String dateIso;
    private String contextStartAtIso;
    private String contextEndAtIso;
    private String channelType;
    private String ownerId;
    @Indexed(name = "chat_event")
    private String eventId;
    private String ownerStatus;
    private Long revision = 1L;
    private String subEventId;
    private String caseOfferId;

    public String getCaseOfferId() { return caseOfferId; }
    public void setCaseOfferId(String caseOfferId) { this.caseOfferId = caseOfferId; }
    private String groupId;
    private Double distanceKm;
    private Integer distanceMetersExact;
    private SupportCaseRecord supportCase;

    @Transient
    private List<ChatMessage> messages = new ArrayList<>();

    public String getMongoId() {
        return mongoId;
    }

    public void setMongoId(String mongoId) {
        this.mongoId = mongoId;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getLastSenderId() {
        return lastSenderId;
    }

    public void setLastSenderId(String lastSenderId) {
        this.lastSenderId = lastSenderId;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds == null ? new ArrayList<>() : memberIds;
    }

    public Integer getUnread() {
        return unread;
    }

    public void setUnread(Integer unread) {
        this.unread = unread;
    }

    public String getDateIso() {
        return dateIso;
    }

    public void setDateIso(String dateIso) {
        this.dateIso = dateIso;
    }

    public String getContextStartAtIso() {
        return contextStartAtIso;
    }

    public void setContextStartAtIso(String contextStartAtIso) {
        this.contextStartAtIso = contextStartAtIso;
    }

    public String getContextEndAtIso() {
        return contextEndAtIso;
    }

    public void setContextEndAtIso(String contextEndAtIso) {
        this.contextEndAtIso = contextEndAtIso;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getOwnerStatus() {
        return ownerStatus;
    }

    public void setOwnerStatus(String ownerStatus) {
        this.ownerStatus = ownerStatus;
    }

    public Long getRevision() {
        return revision;
    }

    public void setRevision(Long revision) {
        this.revision = revision;
    }

    public String getSubEventId() {
        return subEventId;
    }

    public void setSubEventId(String subEventId) {
        this.subEventId = subEventId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getDistanceMetersExact() {
        return distanceMetersExact;
    }

    public void setDistanceMetersExact(Integer distanceMetersExact) {
        this.distanceMetersExact = distanceMetersExact;
    }

    public SupportCaseRecord getSupportCase() {
        return supportCase;
    }

    public void setSupportCase(SupportCaseRecord supportCase) {
        this.supportCase = supportCase;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : messages;
    }

    public static class SupportCaseRecord implements com.raxim.myscoutee.domain.chat.api.ChatDirectory.SupportCase {
        private String status;
        private SupportCaseAssigneeRecord assignee;
        private String updatedAtIso;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public SupportCaseAssigneeRecord getAssignee() {
            return assignee;
        }

        public void setAssignee(SupportCaseAssigneeRecord assignee) {
            this.assignee = assignee;
        }

        public String getUpdatedAtIso() {
            return updatedAtIso;
        }

        public void setUpdatedAtIso(String updatedAtIso) {
            this.updatedAtIso = updatedAtIso;
        }
    }

    public static class SupportCaseAssigneeRecord {
        private String userId;
        private String name;
        private String initials;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getInitials() {
            return initials;
        }

        public void setInitials(String initials) {
            this.initials = initials;
        }
    }

}
