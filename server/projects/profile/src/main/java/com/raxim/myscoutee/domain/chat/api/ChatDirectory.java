package com.raxim.myscoutee.domain.chat.api;

import java.util.List;
import java.util.Optional;

public interface ChatDirectory {
    String unreadVersion(String owner);
    Optional<? extends Chat> findByOwnerUserIdAndChatId(String ownerId, String chatId);
    List<? extends Chat> findByChatId(String chatId);
    interface Chat {
        Integer getUnread(); String getTitle();
        String getChatId(); String getOwnerId(); String getOwnerUserId(); String getChannelType();
        String getSupportBaseGroupId(); List<String> getMemberIds(); SupportCase getSupportCase();
    }
    interface SupportCase { String getStatus(); }
    java.util.Map<String, Integer> supportCaseCounts(String baseGroupId);
}
