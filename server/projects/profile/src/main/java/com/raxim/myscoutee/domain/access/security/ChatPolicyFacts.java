package com.raxim.myscoutee.domain.access.security;

import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import com.raxim.myscoutee.domain.accounts.api.AuthenticatedActor;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.chat.api.ChatDirectory.Chat;
import com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile;
import com.raxim.myscoutee.domain.chat.api.ChatDirectory;
import com.raxim.myscoutee.domain.accounts.api.ProfileDirectory;
import com.raxim.myscoutee.domain.administration.api.AdministratorClassification;

@Component
public class ChatPolicyFacts {
    private final ChatDirectory chatsRepository;
    private final ProfileDirectory usersRepository;
    private final AdministratorClassification adminMenuCounterService;

    public ChatPolicyFacts(
            ChatDirectory chatsRepository,
            ProfileDirectory usersRepository,
            AdministratorClassification adminMenuCounterService) {
        this.chatsRepository = chatsRepository;
        this.usersRepository = usersRepository;
        this.adminMenuCounterService = adminMenuCounterService;
    }

    public String resolveActorUserId(String chatId, String profileId) {
        String actorId = normalizeId(profileId);
        String normalizedChatId = normalizeId(chatId);
        if (actorId == null || normalizedChatId == null) return actorId;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String accountId;
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedActor principal
                && actorId.equals(principal.getUserId()) && principal.getProfileIdentity() != null) {
            accountId = normalizeId(principal.getProfileIdentity().accountUserId());
        } else {
            WorkspaceProfile profile = usersRepository.findByUserId(actorId).orElse(null);
            accountId = profile == null ? null : normalizeId(profile.getAccountUserId());
        }
        if (accountId == null || accountId.equals(actorId)) return actorId;
        return chatsRepository.findByOwnerUserIdAndChatId(accountId, normalizedChatId)
                .filter(chat -> "contact".equals(chat.getChannelType()) && hasChatAccess(chat, accountId))
                .map(chat -> accountId).orElse(actorId);
    }

    public boolean canAccessChat(String chatId, String actorUserId) {
        String normalizedChatId = normalizeId(chatId);
        String normalizedActorUserId = resolveActorUserId(normalizedChatId, actorUserId);
        if (!StringUtils.hasText(normalizedChatId) || !StringUtils.hasText(normalizedActorUserId)) {
            return false;
        }
        Chat viewerChat = chatsRepository
                .findByOwnerUserIdAndChatId(normalizedActorUserId, normalizedChatId)
                .orElse(null);
        if (viewerChat != null) {
            return hasChatAccess(viewerChat, normalizedActorUserId);
        }
        return safeChats(chatsRepository.findByChatId(normalizedChatId)).stream()
                .anyMatch(chat -> hasChatAccess(chat, normalizedActorUserId));
    }

    public boolean canManageSupportChat(String chatId, String actorUserId) {
        String normalizedChatId = normalizeId(chatId);
        String normalizedActorUserId = normalizeId(actorUserId);
        if (!StringUtils.hasText(normalizedChatId) || !StringUtils.hasText(normalizedActorUserId)) {
            return false;
        }
        return isAdminUser(normalizedActorUserId)
                && safeChats(chatsRepository.findByChatId(normalizedChatId)).stream()
                        .anyMatch(chat -> isSupportCaseChat(chat) && sameSupportBase(chat, normalizedActorUserId));
    }

    private boolean hasChatAccess(Chat chat, String actorUserId) {
        if (chat == null || !StringUtils.hasText(actorUserId)) {
            return false;
        }
        String normalizedActorUserId = normalizeId(actorUserId);
        if (normalizedActorUserId == null) return false;
        if (normalizedActorUserId.equals(normalizeId(chat.getOwnerUserId()))) {
            return true;
        }
        if (isSupportCaseChat(chat) && isAdminUser(normalizedActorUserId)) {
            return sameSupportBase(chat, normalizedActorUserId);
        }
        return chat.getMemberIds() != null
                && chat.getMemberIds().stream()
                        .map(this::normalizeId)
                        .anyMatch(normalizedActorUserId::equals);
    }

    private boolean sameSupportBase(Chat chat, String actorId) {
        WorkspaceProfile actor = usersRepository.findByUserId(actorId).orElse(null);
        return actor != null && java.util.Objects.equals(actor.getWorkspaceGroupId(), chat.getSupportBaseGroupId());
    }

    private boolean isSupportCaseChat(Chat chat) {
        if (chat == null) {
            return false;
        }
        String chatId = normalizeId(chat.getChatId());
        return "supportCase".equalsIgnoreCase(safeText(chat.getChannelType()))
                || StringUtils.hasText(safeText(chat.getSupportCase() == null ? null : chat.getSupportCase().getStatus()))
                || (StringUtils.hasText(chatId) && chatId.startsWith("c-support-admin-"));
    }

    private boolean isAdminUser(String userId) {
        WorkspaceProfile user = usersRepository.findByUserId(userId).orElse(null);
        return adminMenuCounterService.isAdminUser(user);
    }

    private List<? extends Chat> safeChats(List<? extends Chat> chats) {
        return chats == null ? List.of() : chats;
    }

    private String normalizeId(String value) {
        String normalizedValue = safeText(value);
        return StringUtils.hasText(normalizedValue) ? normalizedValue : null;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
