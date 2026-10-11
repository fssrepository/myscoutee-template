package com.raxim.myscoutee.domain.chat.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.chat.document.Chat;
import com.raxim.myscoutee.domain.chat.document.Chat.SupportCaseAssigneeRecord;
import com.raxim.myscoutee.domain.chat.document.Chat.SupportCaseRecord;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMemberDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatSupportCaseAssigneeDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatSupportCaseDto;

@Component
public class ChatMapper {
    private static final String SUPPORT_CASE_STATUS_PENDING = "pending";
    private static final String SUPPORT_CASE_STATUS_WARNED = "warned";
    private static final String SUPPORT_CASE_STATUS_PICKED = "picked";
    private static final String SUPPORT_CASE_STATUS_SOLVED = "solved";
    private static final String SUPPORT_CASE_STATUS_BLOCKED = "blocked";

    public ChatDto toDto(Chat record) {
        return toDto(record, Map.of());
    }

    public ChatDto toDto(Chat record, Map<String, ProfileSummary> usersById) {
        if (record == null) {
            return null;
        }
        List<String> memberIds = new ArrayList<>(record.getMemberIds() == null ? List.of() : record.getMemberIds());
        return new ChatDto(
                normalizeId(record.getChatId()),
                safeText(record.getAvatar()),
                safeText(record.getTitle()),
                safeText(record.getLastMessage()),
                safeText(record.getLastSenderId()),
                memberIds,
                toMemberDtos(memberIds, usersById),
                normalizeCount(record.getUnread()),
                record.getDateIso(),
                record.getContextStartAtIso(),
                record.getContextEndAtIso(),
                record.getChannelType(),
                record.getOwnerId(),
                record.getDistanceKm(),
                record.getDistanceMetersExact(),
                toSupportCaseDto(record),
                StringUtils.hasText(safeText(record.getOwnerStatus())) ? safeText(record.getOwnerStatus()) : null,
                normalizeRevision(record.getRevision()));
    }

    public Map<String, ChatDto> toMap(Collection<Chat> records) {
        return toMap(records, Map.of());
    }

    public Map<String, ChatDto> toMap(Collection<Chat> records, Map<String, ProfileSummary> usersById) {
        Map<String, ChatDto> result = new LinkedHashMap<>();
        for (Chat record : records == null ? List.<Chat>of() : records) {
            ChatDto dto = toDto(record, usersById);
            String ownerId = normalizeId(dto == null ? null : dto.ownerId());
            if (StringUtils.hasText(ownerId)) {
                result.put(ownerId, dto);
            }
        }
        return result;
    }

    private long normalizeRevision(Long value) {
        return value == null || value < 1L ? 1L : value;
    }

    private List<ChatMemberDto> toMemberDtos(List<String> memberIds, Map<String, ProfileSummary> usersById) {
        if (memberIds == null || memberIds.isEmpty() || usersById == null || usersById.isEmpty()) {
            return List.of();
        }
        List<ChatMemberDto> members = new ArrayList<>();
        for (String memberId : memberIds) {
            String normalizedMemberId = normalizeId(memberId);
            if (!StringUtils.hasText(normalizedMemberId)) {
                continue;
            }
            ProfileSummary user = usersById.get(normalizedMemberId);
            if (user == null) {
                continue;
            }
            String name = safeText(user.getName());
            String label = StringUtils.hasText(name) ? name : normalizedMemberId;
            members.add(new ChatMemberDto(
                    normalizedMemberId,
                    label,
                    StringUtils.hasText(safeText(user.getInitials())) ? safeText(user.getInitials()) : initialsFromText(label),
                    normalizeGender(user.getGender()),
                    firstImageUrl(user.getImages()),
                    "accepted"));
        }
        return members;
    }

    public ChatMemberDto toMemberDto(ProfileSummary user) {
        return toMemberDto(user, "accepted");
    }

    public ChatMemberDto toMemberDto(ProfileSummary user, String status) {
        if (user == null || !StringUtils.hasText(normalizeId(user.getUserId()))) {
            return null;
        }
        String userId = normalizeId(user.getUserId());
        String name = safeText(user.getName());
        String label = StringUtils.hasText(name) ? name : userId;
        return new ChatMemberDto(
                userId,
                label,
                StringUtils.hasText(safeText(user.getInitials())) ? safeText(user.getInitials()) : initialsFromText(label),
                normalizeGender(user.getGender()),
                firstImageUrl(user.getImages()),
                "pending".equals(safeText(status)) ? "pending" : "accepted");
    }

    private ChatSupportCaseDto toSupportCaseDto(Chat record) {
        if (!isSupportCaseChat(record)) {
            return null;
        }
        SupportCaseRecord supportCase = record.getSupportCase();
        SupportCaseAssigneeRecord assignee = supportCase == null ? null : supportCase.getAssignee();
        ChatSupportCaseAssigneeDto assigneeDto = assignee == null || !StringUtils.hasText(normalizeId(assignee.getUserId()))
                ? null
                : new ChatSupportCaseAssigneeDto(
                        normalizeId(assignee.getUserId()),
                        safeText(assignee.getName()),
                        safeText(assignee.getInitials()));
        return new ChatSupportCaseDto(
                normalizeSupportCaseStatus(supportCase == null ? null : supportCase.getStatus()),
                assigneeDto,
                supportCase == null ? "" : safeText(supportCase.getUpdatedAtIso()));
    }

    private boolean isSupportCaseChat(Chat record) {
        if (record == null) {
            return false;
        }
        return "supportCase".equalsIgnoreCase(safeText(record.getChannelType()))
                || record.getSupportCase() != null
                || normalizeId(record.getChatId()).startsWith("c-support-admin-");
    }

    private String normalizeSupportCaseStatus(String value) {
        String normalized = safeText(value).toLowerCase();
        return switch (normalized) {
            case SUPPORT_CASE_STATUS_WARNED -> SUPPORT_CASE_STATUS_WARNED;
            case SUPPORT_CASE_STATUS_PICKED -> SUPPORT_CASE_STATUS_PICKED;
            case SUPPORT_CASE_STATUS_SOLVED -> SUPPORT_CASE_STATUS_SOLVED;
            case SUPPORT_CASE_STATUS_BLOCKED -> SUPPORT_CASE_STATUS_BLOCKED;
            default -> SUPPORT_CASE_STATUS_PENDING;
        };
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeId(String value) {
        return safeText(value);
    }

    private int normalizeCount(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private String normalizeGender(String value) {
        String normalized = safeText(value).toLowerCase();
        return "woman".equals(normalized) ? "woman" : "man";
    }

    private String firstImageUrl(List<String> images) {
        if (images == null) {
            return null;
        }
        return images.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private String initialsFromText(String value) {
        String text = safeText(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : text.split("\\s+")) {
            if (StringUtils.hasText(part)) {
                builder.append(Character.toUpperCase(part.charAt(0)));
            }
            if (builder.length() >= 2) {
                break;
            }
        }
        return builder.toString();
    }
}
