package com.raxim.myscoutee.domain.chat.dto;

import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatReadAvatarDto(
            String id,
            String initials,
            String gender,
            String imageUrl) {
    }

    public record ChatMemberDto(
            String id,
            String name,
            String initials,
            String gender,
            String imageUrl,
            String status) {
    }

    public record ChatSupportCaseDto(
            String status,
            ChatSupportCaseAssigneeDto assignee,
            String updatedAtIso) {
    }

    public record ChatSupportCaseAssigneeDto(
            String userId,
            String name,
            String initials) {
    }

    public record ChatMembersPageResponseDto(
            List<ChatMemberDto> items,
            Integer total,
            String nextCursor) {
    }

    public record ChatMessagesPageResponseDto(
            List<ChatMessageDto> items,
            Integer total,
            String nextCursor,
            ChatReadReceiptDto read,
            Integer notificationUnread) {
    }

    public record ChatMessageDto(
            String id,
            String clientId,
            String senderId,
            String senderName,
            String senderInitials,
            String senderGender,
            String senderImageUrl,
            String text,
            String sentAtIso,
            Boolean mine,
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
    }

    public record ChatMessageReplyDto(
            String id,
            String sender,
            String text) {
    }

    public record ChatMessageReactionDto(
            String emoji,
            String userId,
            String userName,
            String userInitials,
            String userGender,
            String reactedAtIso) {
    }

    public record ChatMessageAttachmentDto(
            String id,
            String type,
            String title,
            String entityId,
            String assetType,
            String ownerUserId,
            String status,
            String unavailableReason,
            String subtitle,
            String description,
            String url,
            String previewUrl,
            String mimeType,
            Long sizeBytes) {
    }

    public record ChatTypingDto(
            String userId,
            String userName,
            String userInitials,
            String userGender,
            Boolean typing) {
    }

    public record ChatReadReceiptDto(
            String userId,
            String userInitials,
            String userGender,
            String userImageUrl,
            List<String> messageIds,
            String readAtIso,
            Integer unread) {
    }

    public record ChatSocketEventDto(
            String type,
            String chatId,
            ChatMessageDto message,
            ChatTypingDto typing,
            ChatReadReceiptDto read,
            String messageId,
            String clientId,
            String error) {
    }

    public record ChatPostMessageRequestDto(
            String text,
            ChatMessageReplyDto replyTo,
            List<ChatMessageAttachmentDto> attachments) {
    }

    public record ChatMessageMutationRequestDto(
            String text,
            Boolean pinned,
            String emoji,
            List<ChatMessageAttachmentDto> attachments) {
    }

    public record ChatSocketRequestDto(
            String type,
            String messageId,
            String clientId,
            String text,
            ChatMessageReplyDto replyTo,
            List<ChatMessageAttachmentDto> attachments,
            Boolean pinned,
            String emoji,
            Boolean typing,
            List<String> messageIds) {
    }

    public record ChatDeviceUpsertRequestDto(
            String userId,
            String deviceId,
            String platform,
            String firebaseToken,
            Boolean notificationsEnabled) {
    }

    public record ChatDeviceDeleteRequestDto(
            String userId,
            String deviceId,
            String firebaseToken) {
    }

    public record ChatSupportCaseMutationRequestDto(
            String userId,
            String action) {
    }
}
