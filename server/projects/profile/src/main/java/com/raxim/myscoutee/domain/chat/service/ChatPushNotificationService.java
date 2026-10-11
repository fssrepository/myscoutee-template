package com.raxim.myscoutee.domain.chat.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.chat.document.Chat;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.NotificationIntent;

@Service
public class ChatPushNotificationService {
    private static final String APP_BADGE_URL = "/assets/logo/template.svg";
    private static final String APP_FALLBACK_ICON_URL = "/assets/logo/template.svg";
    private static final String DEFAULT_CLICK_URL = "/game";

    private final ProfileSummaries usersRepository;
    private final NotificationDelivery notificationOutboxService;
    private final ChatWebSocketSessionRegistry chatWebSocketSessionRegistry;

    public ChatPushNotificationService(
            ProfileSummaries usersRepository,
            NotificationDelivery notificationOutboxService,
            ChatWebSocketSessionRegistry chatWebSocketSessionRegistry) {
        this.usersRepository = usersRepository;
        this.notificationOutboxService = notificationOutboxService;
        this.chatWebSocketSessionRegistry = chatWebSocketSessionRegistry;
    }

    public void notifyRecipients(List<Chat> chatDocuments, ChatStreamEvent event) {
        if (event == null
                || chatDocuments == null
                || chatDocuments.isEmpty()
                || !"message".equals(safeText(event.type()))
                || isSystemMessage(event)) {
            return;
        }

        Chat referenceChat = chatDocuments.get(0);
        String chatTitle = resolveChatTitle(referenceChat, event);
        String body = buildBody(event);
        String senderAvatarUrl = resolveSenderAvatarUrl(event.senderId());
        String clickUrl = buildClickUrl(event);
        String notificationTag = buildNotificationTag(event);
        long timestampMillis = resolveTimestampMillis(event.sentAtIso());
        List<String> recipients = List.copyOf(resolveRecipientUserIds(
                chatDocuments,
                event.chatId(),
                event.senderId()));
        if (recipients.isEmpty()) {
            return;
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("chatId", safeText(event.chatId()));
        payload.put("messageId", safeText(event.id()));
        payload.put("senderId", safeText(event.senderId()));
        payload.put("senderName", safeText(event.senderName()));
        payload.put("chatTitle", chatTitle);
        payload.put("text", safeText(event.text()));
        if (timestampMillis > 0L) {
            payload.put("timestampMillis", Long.toString(timestampMillis));
        }
        notificationOutboxService.enqueue(new NotificationIntent(
                "chat-message",
                "chat",
                safeText(event.chatId()),
                "chat-members",
                recipients,
                chatTitle,
                body,
                clickUrl,
                senderAvatarUrl,
                APP_BADGE_URL,
                notificationTag,
                payload,
                safeText(event.senderId())));
    }

    private boolean isSystemMessage(ChatStreamEvent event) {
        return "system".equalsIgnoreCase(safeText(event.senderGender()));
    }

    private Set<String> resolveRecipientUserIds(
            List<Chat> chatDocuments,
            String chatId,
            String senderUserId) {
        Set<String> recipients = new LinkedHashSet<>();
        String normalizedSenderUserId = safeText(senderUserId);
        for (Chat chat : chatDocuments) {
            if (chat == null) {
                continue;
            }
            if (StringUtils.hasText(chat.getOwnerUserId()) && !normalizedSenderUserId.equals(chat.getOwnerUserId().trim())) {
                recipients.add(chat.getOwnerUserId().trim());
            }
            if (chat.getMemberIds() == null) {
                continue;
            }
            for (String memberId : chat.getMemberIds()) {
                if (StringUtils.hasText(memberId) && !normalizedSenderUserId.equals(memberId.trim())) {
                    recipients.add(memberId.trim());
                }
            }
        }
        recipients.removeIf(userId -> chatWebSocketSessionRegistry.isUserConnected(chatId, userId));
        return recipients;
    }

    private String resolveChatTitle(Chat referenceChat, ChatStreamEvent event) {
        if (referenceChat != null && StringUtils.hasText(referenceChat.getTitle())) {
            return referenceChat.getTitle().trim();
        }
        if (StringUtils.hasText(event.senderName())) {
            return event.senderName().trim();
        }
        return "Chat";
    }

    private String buildBody(ChatStreamEvent event) {
        String senderName = safeText(event.senderName());
        String preview = previewBody(event.text());
        if (StringUtils.hasText(senderName) && StringUtils.hasText(preview)) {
            return senderName + ": " + preview;
        }
        if (StringUtils.hasText(preview)) {
            return preview;
        }
        if (StringUtils.hasText(senderName)) {
            return senderName + " sent a message.";
        }
        return "New chat message";
    }

    private String resolveSenderAvatarUrl(String senderUserId) {
        ProfileSummary sender = usersRepository.findByUserId(safeText(senderUserId)).orElse(null);
        if (sender != null && sender.getImages() != null) {
            for (String imageUrl : sender.getImages()) {
                String normalizedImageUrl = safeText(imageUrl);
                if (StringUtils.hasText(normalizedImageUrl)) {
                    return normalizedImageUrl;
                }
            }
        }
        return APP_FALLBACK_ICON_URL;
    }

    private String buildClickUrl(ChatStreamEvent event) {
        String chatId = safeText(event.chatId());
        if (!StringUtils.hasText(chatId)) {
            return DEFAULT_CLICK_URL;
        }
        return DEFAULT_CLICK_URL + "?chatId="
                + java.net.URLEncoder.encode(chatId, java.nio.charset.StandardCharsets.UTF_8)
                + "&messageId=" + java.net.URLEncoder.encode(
                        safeText(event.id()), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String buildNotificationTag(ChatStreamEvent event) {
        String chatId = safeText(event.chatId());
        return StringUtils.hasText(chatId) ? "chat-" + chatId : "chat";
    }

    private long resolveTimestampMillis(String sentAtIso) {
        String normalized = safeText(sentAtIso);
        if (!StringUtils.hasText(normalized)) {
            return 0L;
        }
        try {
            return Instant.parse(normalized).toEpochMilli();
        } catch (Exception exception) {
            return 0L;
        }
    }

    private String previewBody(String text) {
        String normalized = safeText(text);
        if (normalized.length() <= 140) {
            return normalized;
        }
        return normalized.substring(0, 137) + "...";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
