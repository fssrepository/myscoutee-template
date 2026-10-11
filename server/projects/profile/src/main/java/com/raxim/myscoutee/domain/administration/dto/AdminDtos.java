package com.raxim.myscoutee.domain.administration.dto;

import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record AdminUserDto(
            String id,
            String name,
            String initials,
            String email,
            String headline,
            String about,
            List<String> images) {}

    public record AdminActionRequestDto(String adminUserId, String message, String reportId) {}

    public record AdminReviewResolveRequestDto(String adminUserId, Boolean resolved) {}

    public record AdminChatMessageDto(
            String id,
            String sender,
            String senderUserId,
            String senderInitials,
            String senderGender,
            String text,
            String time,
            String sentAtIso) {}

    public record AdminDashboardDto(
            AdminUserDto activeAdmin,
            List<AdminReportedUserDto> reportedUsers,
            List<AdminReportedUserDto> blockedUsers,
            List<AdminFeedbackDto> feedback,
            AdminReviewCountsDto reviewCounts) {}

    public record AdminReviewCountsDto(
            Integer reportsUnresolved,
            Integer reportsResolved,
            Integer feedbackUnresolved,
            Integer feedbackResolved) {}

    public record AdminReportedUserDto(
            String userId,
            String name,
            String initials,
            String gender,
            String city,
            String imageUrl,
            String profileStatus,
            Integer reportCount,
            String lastReportedAtIso,
            Boolean hasSupportChat,
            Integer supportChatUnread,
            String supportChatId,
            List<AdminReportDto> reports) {}

    public record AdminReportDto(
            String id,
            String reporterUserId,
            String reporterName,
            String reporterImageUrl,
            String targetUserId,
            String handle,
            String reason,
            String details,
            String eventId,
            String eventTitle,
            String eventStartAtIso,
            String memberEntryId,
            String sourceType,
            String sourceId,
            String sourceText,
            String chatId,
            String messageId,
            String assetId,
            String assetType,
            String chatTitle,
            List<AdminChatMessageDto> chatMessages,
            String createdDate,
            String warnedAtIso,
            String warnedByAdminUserId,
            String resolvedAtIso,
            String resolvedByAdminUserId) {}

    public record AdminFeedbackDto(
            String id,
            String userId,
            String userName,
            String userImageUrl,
            String category,
            String subject,
            String details,
            String createdDate,
            String resolvedAtIso,
            String resolvedByAdminUserId) {}
}
