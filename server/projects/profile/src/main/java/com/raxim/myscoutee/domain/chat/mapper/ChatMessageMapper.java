package com.raxim.myscoutee.domain.chat.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.chat.document.ChatMessage;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatAvatarRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatMessageAttachmentRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatMessageReactionRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatMessageReplyRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatReadAvatarRecord;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageAttachmentDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageReactionDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageReplyDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatReadAvatarDto;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.chat.service.ChatStreamEvent;

@Component
public class ChatMessageMapper {
    private final ProfileSummaries usersRepository;

    public ChatMessageMapper(ProfileSummaries usersRepository) {
        this.usersRepository = usersRepository;
    }

    /** Fill the existing per-page cache in batches before mapping; missing profiles are cached as absent. */
    public List<ChatMessageDto> toDtos(List<ChatMessage> messages, String viewerUserId) {
        Map<String, ProfileSummary> userCache = new java.util.LinkedHashMap<>();
        for (ChatMessage message : messages) {
            if (message == null) continue;
            addProfileId(userCache, message.getSenderAvatar() == null ? null : message.getSenderAvatar().getId());
            if (StringUtils.hasText(message.getDeletedAtIso())) continue;
            for (var reader : safeReadBy(message.getReadBy())) addProfileId(userCache, reader == null ? null : reader.getId());
            for (var reaction : safeReactions(message.getReactions())) addProfileId(userCache, reaction == null ? null : reaction.getUserId());
        }
        List<String> ids = new java.util.ArrayList<>(userCache.keySet());
        for (int offset = 0; offset < ids.size(); offset += 1000) {
            for (ProfileSummary user : usersRepository.findByUserIdIn(ids.subList(offset, Math.min(ids.size(), offset + 1000)))) {
                if (user != null && userCache.containsKey(normalizeId(user.getUserId()))) {
                    userCache.putIfAbsent(normalizeId(user.getUserId()), user);
                }
            }
        }
        return messages.stream().map(message -> toDto(message, viewerUserId, userCache)).toList();
    }

    private void addProfileId(Map<String, ProfileSummary> userCache, String userId) {
        String normalized = normalizeId(userId);
        if (StringUtils.hasText(normalized)) userCache.putIfAbsent(normalized, null);
    }

    public ChatMessageDto toDto(ChatMessage message, String viewerUserId) {
        return toDto(message, viewerUserId, null);
    }

    public ChatMessageDto toDto(ChatMessage message, String viewerUserId, Map<String, ProfileSummary> userCache) {
        ChatAvatarRecord senderAvatar = message == null ? null : message.getSenderAvatar();
        String senderId = senderAvatar == null ? "" : safeText(senderAvatar.getId());
        ProfileSummary sender = findUser(senderId, userCache);
        boolean deletedSender = isDeletedOrPurgedUser(sender, senderId);
        String senderName = deletedSender ? deletedUserName() : message == null ? "" : safeText(message.getSender());
        String senderInitials = deletedSender ? deletedUserInitials() : senderAvatar == null ? buildInitials(senderId) : safeText(senderAvatar.getInitials());
        String senderGender = deletedSender ? "deleted" : senderAvatar == null ? "man" : normalizeGender(senderAvatar.getGender());
        boolean deleted = StringUtils.hasText(message == null ? "" : message.getDeletedAtIso());
        return new ChatMessageDto(
                message == null ? "" : safeText(message.getId()),
                message == null ? "" : safeText(message.getClientId()),
                senderId,
                senderName,
                senderInitials,
                senderGender,
                "deleted".equals(senderGender) ? "" : resolveUserImageUrl(sender),
                deleted ? "" : presentationText(message),
                message == null ? Instant.now().toString() : safeText(message.getSentAtIso()),
                Objects.equals(senderId, normalizeId(viewerUserId)),
                deleted ? List.of() : safeReadBy(message == null ? null : message.getReadBy()).stream()
                        .map(readRecord -> toReadAvatarDto(readRecord, userCache))
                        .toList(),
                message == null ? "" : safeText(message.getDeletedAtIso()),
                message == null ? "" : safeText(message.getDeletedByUserId()),
                message == null ? "" : safeText(message.getDeletedByName()),
                deleted ? "" : message == null ? "" : safeText(message.getEditedAtIso()),
                deleted ? "" : message == null ? "" : safeText(message.getPinnedAtIso()),
                deleted ? "" : message == null ? "" : safeText(message.getPinnedByUserId()),
                deleted ? null : toReplyDto(message == null ? null : message.getReplyTo()),
                deleted ? List.of() : safeReactions(message == null ? null : message.getReactions()).stream()
                        .map(reactionRecord -> toReactionDto(reactionRecord, userCache))
                        .toList(),
                deleted ? List.of() : safeAttachments(message == null ? null : message.getAttachments()).stream()
                        .map(this::toAttachmentDto)
                        .toList());
    }

    public ChatStreamEvent toStreamEvent(String chatId, ChatMessage message) {
        ChatAvatarRecord senderAvatar = message == null ? null : message.getSenderAvatar();
        String senderId = senderAvatar == null ? "" : safeText(senderAvatar.getId());
        ProfileSummary sender = findUser(senderId);
        boolean deletedSender = isDeletedOrPurgedUser(sender, senderId);
        String senderGender = deletedSender ? "deleted" : senderAvatar == null ? "man" : normalizeGender(senderAvatar.getGender());
        return ChatStreamEvent.message(
                chatId,
                message == null ? "" : safeText(message.getId()),
                message == null ? "" : safeText(message.getClientId()),
                senderId,
                deletedSender ? deletedUserName() : message == null ? "" : safeText(message.getSender()),
                deletedSender ? deletedUserInitials() : senderAvatar == null ? "CH" : safeText(senderAvatar.getInitials()),
                senderGender,
                "deleted".equals(senderGender) ? "" : resolveUserImageUrl(sender),
                presentationText(message),
                message == null ? "" : safeText(message.getSentAtIso()),
                safeReadBy(message == null ? null : message.getReadBy()).stream()
                        .map(this::toReadAvatarDto)
                        .toList(),
                message == null ? "" : safeText(message.getDeletedAtIso()),
                message == null ? "" : safeText(message.getDeletedByUserId()),
                message == null ? "" : safeText(message.getDeletedByName()),
                message == null ? "" : safeText(message.getEditedAtIso()),
                message == null ? "" : safeText(message.getPinnedAtIso()),
                message == null ? "" : safeText(message.getPinnedByUserId()),
                toReplyDto(message == null ? null : message.getReplyTo()),
                safeReactions(message == null ? null : message.getReactions()).stream()
                        .map(this::toReactionDto)
                        .toList(),
                safeAttachments(message == null ? null : message.getAttachments()).stream()
                        .map(this::toAttachmentDto)
                        .toList());
    }

    public ChatReadAvatarDto toReadAvatarDto(ChatReadAvatarRecord record) {
        String userId = record == null ? "" : safeText(record.getId());
        return toReadAvatarDto(record, findUser(userId));
    }

    public ChatReadAvatarDto toReadAvatarDto(ChatReadAvatarRecord record, Map<String, ProfileSummary> userCache) {
        String userId = record == null ? "" : safeText(record.getId());
        return toReadAvatarDto(record, findUser(userId, userCache));
    }

    public ChatReadAvatarDto toReadAvatarDto(ChatReadAvatarRecord record, ProfileSummary user) {
        String userId = record == null ? "" : safeText(record.getId());
        boolean deletedUser = isDeletedOrPurgedUser(user, userId);
        String gender = deletedUser ? "deleted" : record == null ? "man" : normalizeGender(record.getGender());
        return new ChatReadAvatarDto(
                userId,
                deletedUser ? deletedUserInitials() : record == null ? "" : safeText(record.getInitials()),
                gender,
                "deleted".equals(gender) ? "" : resolveUserImageUrl(user));
    }

    public ChatReadAvatarDto emptyReadAvatarDto() {
        return new ChatReadAvatarDto("", "", "man", "");
    }

    public String summary(ChatMessage message) {
        if (StringUtils.hasText(message == null ? "" : message.getDeletedAtIso())) {
            return "Deleted a message";
        }
        String text = message == null ? "" : safeText(message.getText());
        if (StringUtils.hasText(text)) {
            return text;
        }
        ChatMessageAttachmentRecord attachment = safeAttachments(message == null ? null : message.getAttachments()).stream()
                .findFirst()
                .orElse(null);
        if (attachment == null) {
            return "";
        }
        String type = safeText(attachment.getType()).toLowerCase(Locale.US);
        return switch (type) {
            case "image" -> "Sent an image";
            case "event" -> "Shared an event";
            case "asset" -> "Shared an asset";
            case "voice" -> "Sent a voice clip";
            case "poll" -> "Created a poll";
            case "link" -> "Shared a link";
            default -> StringUtils.hasText(attachment.getTitle()) ? safeText(attachment.getTitle()) : "Shared an attachment";
        };
    }

    private ChatMessageReplyDto toReplyDto(ChatMessageReplyRecord record) {
        if (record == null || !StringUtils.hasText(record.getId())) {
            return null;
        }
        return new ChatMessageReplyDto(
                safeText(record.getId()),
                safeText(record.getSender()),
                safeText(record.getText()));
    }

    private ChatMessageReactionDto toReactionDto(ChatMessageReactionRecord record) {
        String userId = record == null ? "" : safeText(record.getUserId());
        return toReactionDto(record, findUser(userId));
    }

    private ChatMessageReactionDto toReactionDto(ChatMessageReactionRecord record, Map<String, ProfileSummary> userCache) {
        String userId = record == null ? "" : safeText(record.getUserId());
        return toReactionDto(record, findUser(userId, userCache));
    }

    private ChatMessageReactionDto toReactionDto(ChatMessageReactionRecord record, ProfileSummary user) {
        String userId = record == null ? "" : safeText(record.getUserId());
        boolean deletedUser = isDeletedOrPurgedUser(user, userId);
        return new ChatMessageReactionDto(
                record == null ? "" : safeText(record.getEmoji()),
                userId,
                deletedUser ? deletedUserName() : record == null ? "" : safeText(record.getUserName()),
                deletedUser ? deletedUserInitials() : record == null ? "" : safeText(record.getUserInitials()),
                deletedUser ? "deleted" : record == null ? "man" : normalizeGender(record.getUserGender()),
                record == null ? "" : safeText(record.getReactedAtIso()));
    }

    private ChatMessageAttachmentDto toAttachmentDto(ChatMessageAttachmentRecord record) {
        return new ChatMessageAttachmentDto(
                record == null ? "" : safeText(record.getId()),
                record == null ? "link" : safeText(record.getType()),
                record == null ? "" : safeText(record.getTitle()),
                record == null ? "" : safeText(record.getEntityId()),
                record == null ? "" : safeText(record.getAssetType()),
                record == null ? "" : safeText(record.getOwnerUserId()),
                record == null ? "available" : normalizeAttachmentStatus(record.getStatus()),
                record == null ? "" : safeText(record.getUnavailableReason()),
                record == null ? "" : safeText(record.getSubtitle()),
                record == null ? "" : presentationAttachmentDescription(record),
                record == null ? "" : safeText(record.getUrl()),
                record == null ? "" : safeText(record.getPreviewUrl()),
                record == null ? "" : safeText(record.getMimeType()),
                record == null ? null : record.getSizeBytes());
    }

    private String presentationAttachmentDescription(ChatMessageAttachmentRecord record) {
        if (record == null) {
            return "";
        }
        String type = safeText(record.getType()).toLowerCase(Locale.US);
        if (!"poll".equals(type)) {
            return safeText(record.getDescription());
        }
        return maskDeletedPollVotes(record.getDescription());
    }

    private String maskDeletedPollVotes(String description) {
        String normalizedDescription = safeText(description);
        if (!StringUtils.hasText(normalizedDescription)) {
            return "";
        }
        try {
            Document root = Document.parse(normalizedDescription);
            Object optionsValue = root.get("options");
            if (!(optionsValue instanceof List<?> optionNodes)) {
                return normalizedDescription;
            }
            boolean changed = false;
            for (Object option : optionNodes) {
                if (!(option instanceof Document optionDocument)) {
                    continue;
                }
                Object votesValue = optionDocument.get("votes");
                if (!(votesValue instanceof List<?> voteNodes)) {
                    continue;
                }
                for (Object vote : voteNodes) {
                    if (!(vote instanceof Document voteObject)) {
                        continue;
                    }
                    String userId = safeText(Objects.toString(voteObject.get("userId"), ""));
                    if (!StringUtils.hasText(userId) || !isDeletedOrPurgedUser(findUser(userId), userId)) {
                        continue;
                    }
                    changed = putIfChanged(voteObject, "name", deletedUserName()) || changed;
                    changed = putIfChanged(voteObject, "initials", deletedUserInitials()) || changed;
                    changed = putIfChanged(voteObject, "gender", "deleted") || changed;
                    changed = putIfChanged(voteObject, "imageUrl", "") || changed;
                }
            }
            return changed ? root.toJson() : normalizedDescription;
        } catch (Exception ignored) {
            return normalizedDescription;
        }
    }

    private boolean putIfChanged(Document node, String key, String value) {
        if (Objects.equals(safeText(Objects.toString(node.get(key), "")), safeText(value))) {
            return false;
        }
        node.put(key, safeText(value));
        return true;
    }

    private ProfileSummary findUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return usersRepository.findByUserId(userId).orElse(null);
    }

    private ProfileSummary findUser(String userId, Map<String, ProfileSummary> userCache) {
        String normalizedUserId = normalizeId(userId);
        if (!StringUtils.hasText(normalizedUserId)) {
            return null;
        }
        if (userCache == null) {
            return findUser(normalizedUserId);
        }
        if (userCache.containsKey(normalizedUserId)) {
            return userCache.get(normalizedUserId);
        }
        ProfileSummary user = findUser(normalizedUserId);
        userCache.put(normalizedUserId, user);
        return user;
    }

    private boolean isDeletedOrPurgedUser(ProfileSummary user, String userId) {
        if (!StringUtils.hasText(safeText(userId))) {
            return false;
        }
        return user == null
                || "D".equalsIgnoreCase(safeText(user.getStatus()))
                || "deleted".equalsIgnoreCase(safeText(user.getProfileStatus()));
    }

    private String deletedUserName() {
        return "Deleted user";
    }

    private String deletedUserInitials() {
        return "DU";
    }

    private String resolveUserImageUrl(ProfileSummary user) {
        if (user == null || user.getImages() == null) {
            return "";
        }
        return user.getImages().stream()
                .map(this::safeText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String buildInitials(String value) {
        String normalized = safeText(value);
        if (!StringUtils.hasText(normalized)) {
            return "CH";
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.US);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.US);
    }

    private String normalizeGender(String value) {
        String normalized = safeText(value).toLowerCase(Locale.US);
        if ("deleted".equals(normalized)) {
            return "deleted";
        }
        if ("system".equals(normalized)) {
            return "system";
        }
        if (normalized.startsWith("w") || normalized.startsWith("f")) {
            return "woman";
        }
        return "man";
    }

    private String presentationText(ChatMessage message) {
        return message == null ? "" : safeText(message.getText());
    }

    private String normalizeAttachmentStatus(String value) {
        return "unavailable".equals(safeText(value)) ? "unavailable" : "available";
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

    private String normalizeId(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
