package com.raxim.myscoutee.domain.chat.service;

import java.util.List;
import java.util.Objects;

import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageAttachmentDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageReactionDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageReplyDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatReadAvatarDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatReadReceiptDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatSocketEventDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatTypingDto;

public record ChatStreamEvent(
        String type,
        String chatId,
        String id,
        String clientId,
        String senderId,
        String senderName,
        String senderInitials,
        String senderGender,
        String senderImageUrl,
        String text,
        String sentAtIso,
        List<ChatReadAvatarDto> readBy,
        String deletedAtIso,
        String deletedByUserId,
        String deletedByName,
        String editedAtIso,
        String pinnedAtIso,
        String pinnedByUserId,
        ChatMessageReplyDto replyTo,
        List<ChatMessageReactionDto> reactions,
        List<ChatMessageAttachmentDto> attachments,
        Boolean typing,
        List<String> messageIds,
        String readAtIso,
        Integer unread) {

    public static ChatStreamEvent message(
            String chatId,
            String id,
            String clientId,
            String senderId,
            String senderName,
            String senderInitials,
            String senderGender,
            String senderImageUrl,
            String text,
            String sentAtIso,
            List<ChatReadAvatarDto> readBy,
            String deletedAtIso,
            String deletedByUserId,
            String deletedByName,
            String editedAtIso,
            String pinnedAtIso,
            String pinnedByUserId,
            ChatMessageReplyDto replyTo,
            List<ChatMessageReactionDto> reactions,
            List<ChatMessageAttachmentDto> attachments) {
        return new ChatStreamEvent(
                "message",
                chatId,
                id,
                clientId,
                senderId,
                senderName,
                senderInitials,
                senderGender,
                senderImageUrl,
                text,
                sentAtIso,
                readBy,
                deletedAtIso,
                deletedByUserId,
                deletedByName,
                editedAtIso,
                pinnedAtIso,
                pinnedByUserId,
                replyTo,
                reactions,
                attachments,
                null,
                null,
                null,
                null);
    }

    public static ChatStreamEvent typing(
            String chatId,
            String senderId,
            String senderName,
            String senderInitials,
            String senderGender,
            boolean typing) {
        return new ChatStreamEvent(
                "typing",
                chatId,
                null,
                null,
                senderId,
                senderName,
                senderInitials,
                senderGender,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                typing,
                null,
                null,
                null);
    }

    public static ChatStreamEvent read(
            String chatId,
            String senderId,
            String senderInitials,
            String senderGender,
            String senderImageUrl,
            List<String> messageIds,
            String readAtIso,
            Integer unread) {
        return new ChatStreamEvent(
                "read",
                chatId,
                null,
                null,
                senderId,
                null,
                senderInitials,
                senderGender,
                senderImageUrl,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                messageIds,
                readAtIso,
                unread);
    }

    public ChatSocketEventDto toSocketEvent(String viewerUserId) {
        if ("typing".equalsIgnoreCase(type)) {
            return new ChatSocketEventDto(
                    "typing",
                    chatId,
                    null,
                    new ChatTypingDto(
                            senderId,
                            senderName,
                            senderInitials,
                            senderGender,
                            Boolean.TRUE.equals(typing)),
                    null,
                    null,
                    null,
                    null);
        }
        if ("read".equalsIgnoreCase(type)) {
            return new ChatSocketEventDto(
                    "read",
                    chatId,
                    null,
                    null,
                    new ChatReadReceiptDto(
                            senderId,
                            senderInitials,
                            senderGender,
                            senderImageUrl,
                            messageIds == null ? List.of() : List.copyOf(messageIds),
                            readAtIso,
                            unread),
                    null,
                    null,
                    null);
        }
        return new ChatSocketEventDto(
                "message",
                chatId,
                new ChatMessageDto(
                        id,
                        clientId,
                        senderId,
                        senderName,
                        senderInitials,
                        senderGender,
                        senderImageUrl,
                        text,
                        sentAtIso,
                        Objects.equals(senderId, viewerUserId),
                        readBy == null ? List.of() : List.copyOf(readBy),
                        deletedAtIso,
                        deletedByUserId,
                        deletedByName,
                        editedAtIso,
                        pinnedAtIso,
                        pinnedByUserId,
                        replyTo,
                        reactions == null ? List.of() : List.copyOf(reactions),
                        attachments == null ? List.of() : List.copyOf(attachments)),
                null,
                null,
                null,
                null,
                null);
    }
}
