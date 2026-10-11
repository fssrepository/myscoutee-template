package com.raxim.myscoutee.domain.chat.service;
import java.util.*;
import java.time.Instant;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.PageRequest;
import com.raxim.myscoutee.domain.chat.api.ChatPrivacy;
import com.raxim.myscoutee.domain.chat.document.Chat;
import com.raxim.myscoutee.domain.chat.document.ChatMessage;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.*;
import com.raxim.myscoutee.domain.chat.repository.*;
import com.raxim.myscoutee.domain.media.api.MediaCleanup;
@Service
public class ChatPrivacyService implements ChatPrivacy {
    private final ChatsRepository chatsRepository;
    private final ChatMessagesRepository chatMessagesRepository;
    private final MediaCleanup mediaStorageService;
    public ChatPrivacyService(ChatsRepository chats, ChatMessagesRepository messages, MediaCleanup media) {
        chatsRepository = chats; chatMessagesRepository = messages; mediaStorageService = media;
    }
    public void purgeUserChatActivity(String userId) {
        Map<String, Chat> chatsByKey = new LinkedHashMap<>();
        addChats(chatsByKey, chatsRepository.findByOwnerUserId(userId));
        addChats(chatsByKey, chatsRepository.findByMemberIdsContaining(userId));
        Set<String> chatIds = new LinkedHashSet<>();
        addMessageChatIds(chatIds, chatMessagesRepository.findByMessageSenderUserId(userId));
        addMessageChatIds(chatIds, chatMessagesRepository.findByMessageReadByUserId(userId));
        addMessageChatIds(chatIds, chatMessagesRepository.findByMessageReactionUserId(userId));
        addMessageChatIds(chatIds, chatMessagesRepository.findByPollVoterUserId(userId));
        for (Chat chat : chatsByKey.values()) {
            String chatId = safeText(chat == null ? null : chat.getChatId());
            if (StringUtils.hasText(chatId)) {
                chatIds.add(chatId);
            }
        }
        for (String chatId : chatIds) {
            addChats(chatsByKey, chatsRepository.findByChatId(chatId));
        }
        if (chatsByKey.isEmpty() && chatIds.isEmpty()) {
            return;
        }

        String purgeAtIso = Instant.now().toString();
        Set<String> summaryRefreshChatIds = new LinkedHashSet<>();
        for (String chatId : chatIds) {
            String normalizedChatId = safeText(chatId);
            if (StringUtils.hasText(normalizedChatId)
                    && purgeChatMessagesInBatches(normalizedChatId, userId, purgeAtIso)) {
                summaryRefreshChatIds.add(normalizedChatId);
            }
        }

        List<Chat> changedChats = new ArrayList<>();
        for (Chat chat : chatsByKey.values()) {
            if (chat == null) {
                continue;
            }
            boolean changed = removeMember(chat, userId);
            if (userId.equals(safeText(chat.getOwnerUserId()))) {
                chat.setOwnerUserId("deleted-user");
                changed = true;
            }
            if (changed) {
                String chatId = safeText(chat.getChatId());
                if (StringUtils.hasText(chatId)) {
                    summaryRefreshChatIds.add(chatId);
                }
                changedChats.add(chat);
            }
        }
        if (!changedChats.isEmpty()) {
            chatsRepository.saveAll(changedChats);
        }
        for (String chatId : summaryRefreshChatIds) {
            refreshChatSummary(chatId);
        }
    }

    private void addChats(Map<String, Chat> chatsByKey, List<Chat> chats) {
        for (Chat chat : chats == null ? List.<Chat>of() : chats) {
            if (chat == null) {
                continue;
            }
            String key = safeText(chat.getMongoId());
            if (!StringUtils.hasText(key)) {
                key = safeText(chat.getOwnerUserId()) + ":" + safeText(chat.getChatId());
            }
            if (StringUtils.hasText(key)) {
                chatsByKey.putIfAbsent(key, chat);
            }
        }
    }

    private void addMessageChatIds(Set<String> chatIds, List<ChatMessage> messages) {
        if (chatIds == null) {
            return;
        }
        for (ChatMessage message : messages == null ? List.<ChatMessage>of() : messages) {
            String chatId = safeText(message == null ? null : message.getChatId());
            if (StringUtils.hasText(chatId)) {
                chatIds.add(chatId);
            }
        }
    }

    private boolean purgeChatMessagesInBatches(String chatId, String userId, String purgeAtIso) {
        int pageSize = 100;
        long total = chatMessagesRepository.countByChatId(chatId);
        Set<String> purgedMessageIds = new LinkedHashSet<>();
        for (int offset = 0; offset < total; offset += pageSize) {
            List<ChatMessage> page = chatMessagesRepository.findByChatIdOrderBySentAtIsoDesc(
                    chatId,
                    PageRequest.of(offset / pageSize, pageSize));
            for (ChatMessage message : page) {
                String messageId = safeText(message == null ? null : message.getId());
                if (StringUtils.hasText(messageId) && isSentByUser(message, userId)) {
                    purgedMessageIds.add(messageId);
                }
            }
        }

        boolean changedAny = false;
        for (int offset = 0; offset < total; offset += pageSize) {
            List<ChatMessage> page = chatMessagesRepository.findByChatIdOrderBySentAtIsoDesc(
                    chatId,
                    PageRequest.of(offset / pageSize, pageSize));
            List<ChatMessage> changedMessages = new ArrayList<>();
            for (ChatMessage message : page) {
                if (message == null) {
                    continue;
                }
                boolean changed = false;
                if (isSentByUser(message, userId)) {
                    purgeMessageMedia(message);
                    scrubSentMessage(message, userId, purgeAtIso);
                    changed = true;
                }
                changed = removeReadsByUser(message, userId) || changed;
                changed = removeReactionsByUser(message, userId) || changed;
                changed = removePollVotesByUser(message, userId) || changed;
                changed = scrubReplyToPurgedMessage(message, purgedMessageIds) || changed;
                changed = clearPinByUser(message, userId) || changed;
                if (userId.equals(safeText(message.getDeletedByUserId()))) {
                    message.setDeletedByName("Deleted user");
                    changed = true;
                }
                if (changed) {
                    message.refreshMessageSearchFields();
                    changedMessages.add(message);
                }
            }
            if (!changedMessages.isEmpty()) {
                chatMessagesRepository.saveAll(changedMessages);
                changedAny = true;
            }
        }
        return changedAny;
    }

    private boolean removeMember(Chat chat, String userId) {
        List<String> members = chat.getMemberIds() == null ? List.of() : chat.getMemberIds();
        List<String> retained = members.stream()
                .filter(memberId -> !userId.equals(safeText(memberId)))
                .toList();
        if (retained.size() == members.size()) {
            return false;
        }
        chat.setMemberIds(new ArrayList<>(retained));
        return true;
    }

    private boolean isSentByUser(ChatMessage message, String userId) {
        ChatAvatarRecord senderAvatar = message == null ? null : message.getSenderAvatar();
        return senderAvatar != null && userId.equals(safeText(senderAvatar.getId()));
    }

    private void purgeMessageMedia(ChatMessage message) {
        for (ChatMessageAttachmentRecord attachment : message.getAttachments() == null ? List.<ChatMessageAttachmentRecord>of() : message.getAttachments()) {
            if (attachment == null) {
                continue;
            }
            String type = safeText(attachment.getType()).toLowerCase();
            if ("voice".equals(type) || "image".equals(type)) {
                deletePurgeMedia(attachment.getUrl());
                deletePurgeMedia(attachment.getPreviewUrl());
            }
        }
    }

    private void scrubSentMessage(ChatMessage message, String userId, String purgeAtIso) {
        message.setSender("Deleted user");
        ChatAvatarRecord senderAvatar = message.getSenderAvatar();
        if (senderAvatar != null) {
            senderAvatar.setInitials("DU");
            senderAvatar.setGender("deleted");
        }
        message.setText("");
        message.setAttachments(new ArrayList<>());
        message.setReadBy(new ArrayList<>());
        message.setReactions(new ArrayList<>());
        message.setEditedAtIso(null);
        message.setPinnedAtIso(null);
        message.setPinnedByUserId(null);
        if (!StringUtils.hasText(message.getDeletedAtIso())) {
            message.setDeletedAtIso(purgeAtIso);
            message.setDeletedByUserId(userId);
        }
        message.setDeletedByName("Deleted user");
    }

    private boolean removeReadsByUser(ChatMessage message, String userId) {
        List<ChatReadAvatarRecord> readBy = message.getReadBy() == null ? List.of() : message.getReadBy();
        List<ChatReadAvatarRecord> retained = readBy.stream()
                .filter(reader -> reader != null && !userId.equals(safeText(reader.getId())))
                .toList();
        if (retained.size() == readBy.size()) {
            return false;
        }
        message.setReadBy(new ArrayList<>(retained));
        return true;
    }

    private boolean removeReactionsByUser(ChatMessage message, String userId) {
        List<ChatMessageReactionRecord> reactions = message.getReactions() == null ? List.of() : message.getReactions();
        List<ChatMessageReactionRecord> retained = reactions.stream()
                .filter(reaction -> reaction != null && !userId.equals(safeText(reaction.getUserId())))
                .toList();
        if (retained.size() == reactions.size()) {
            return false;
        }
        message.setReactions(new ArrayList<>(retained));
        return true;
    }

    private boolean removePollVotesByUser(ChatMessage message, String userId) {
        boolean changed = false;
        for (ChatMessageAttachmentRecord attachment : message.getAttachments() == null ? List.<ChatMessageAttachmentRecord>of() : message.getAttachments()) {
            if (attachment == null || !"poll".equalsIgnoreCase(safeText(attachment.getType()))) {
                continue;
            }
            String nextDescription = removePollVotes(attachment.getDescription(), userId);
            if (nextDescription != null && !Objects.equals(safeText(attachment.getDescription()), nextDescription)) {
                attachment.setDescription(nextDescription);
                changed = true;
            }
        }
        return changed;
    }

    private String removePollVotes(String description, String userId) {
        String normalizedDescription = safeText(description);
        if (!StringUtils.hasText(normalizedDescription) || !normalizedDescription.contains(userId)) {
            return null;
        }
        try {
            Document root = Document.parse(normalizedDescription);
            Object optionsValue = root.get("options");
            if (!(optionsValue instanceof List<?> optionNodes)) {
                return null;
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
                List<Object> mutableVotes = new ArrayList<>(voteNodes);
                int previousSize = mutableVotes.size();
                mutableVotes.removeIf(vote -> vote instanceof Document voteDocument
                        && userId.equals(safeText(Objects.toString(voteDocument.get("userId"), ""))));
                if (mutableVotes.size() != previousSize) {
                    optionDocument.put("votes", mutableVotes);
                    changed = true;
                }
            }
            return changed ? root.toJson() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean scrubReplyToPurgedMessage(ChatMessage message, Set<String> purgedMessageIds) {
        ChatMessageReplyRecord replyTo = message.getReplyTo();
        if (replyTo == null || !purgedMessageIds.contains(safeText(replyTo.getId()))) {
            return false;
        }
        replyTo.setSender("Deleted user");
        replyTo.setText("Message unavailable");
        return true;
    }

    private boolean clearPinByUser(ChatMessage message, String userId) {
        if (!userId.equals(safeText(message.getPinnedByUserId()))) {
            return false;
        }
        message.setPinnedAtIso(null);
        message.setPinnedByUserId(null);
        return true;
    }

    private void refreshChatSummary(String chatId) {
        String normalizedChatId = safeText(chatId);
        if (!StringUtils.hasText(normalizedChatId)) {
            return;
        }
        ChatMessage latest = chatMessagesRepository
                .findByChatIdOrderBySentAtIsoDesc(normalizedChatId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
        List<Chat> changedChats = new ArrayList<>();
        for (Chat chat : chatsRepository.findByChatId(normalizedChatId)) {
            if (chat == null) {
                continue;
            }
            if (latest == null) {
                chat.setLastMessage("");
                chat.setLastSenderId("");
            } else {
                ChatAvatarRecord senderAvatar = latest.getSenderAvatar();
                chat.setLastSenderId(senderAvatar == null ? "" : safeText(senderAvatar.getId()));
                chat.setLastMessage(messageSummary(latest));
            }
            changedChats.add(chat);
        }
        if (!changedChats.isEmpty()) {
            chatsRepository.saveAll(changedChats);
        }
    }

    private String messageSummary(ChatMessage message) {
        if (StringUtils.hasText(message.getDeletedAtIso())) {
            return "Deleted a message";
        }
        String text = safeText(message.getText());
        if (StringUtils.hasText(text)) {
            return text;
        }
        ChatMessageAttachmentRecord attachment = message.getAttachments() == null || message.getAttachments().isEmpty()
                ? null
                : message.getAttachments().get(0);
        if (attachment == null) {
            return "";
        }
        return switch (safeText(attachment.getType()).toLowerCase()) {
            case "image" -> "Sent an image";
            case "event" -> "Shared an event";
            case "asset" -> "Shared an asset";
            case "voice" -> "Sent a voice clip";
            case "poll" -> "Created a poll";
            case "link" -> "Shared a link";
            default -> StringUtils.hasText(attachment.getTitle()) ? safeText(attachment.getTitle()) : "Shared an attachment";
        };
    }

    private void deletePurgeMedia(String mediaUrlOrKey) {
        mediaStorageService.deletePublicObjectOrThrow(mediaUrlOrKey);
    }
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
