package com.raxim.myscoutee.domain.chat.api;
public interface SupportMessages {
    com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageDto sendAdminSupportMessage(
            String targetUserId,
            String adminUserId,
            String adminName,
            String adminInitials,
            String text,
            String supportStatus);
    com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageDto sendAdminSupportMessage(String targetUserId, String adminUserId, String adminName,
            String adminInitials, String text, String supportStatus, String stableMessageId);
}
