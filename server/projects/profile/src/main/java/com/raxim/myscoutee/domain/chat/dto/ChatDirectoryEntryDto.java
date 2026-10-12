package com.raxim.myscoutee.domain.chat.dto;

import java.util.List;
import com.raxim.myscoutee.domain.chat.api.ChatDirectory;

/** Detached projection of the existing directory view; excludes messages and persistence identity. */
public record ChatDirectoryEntryDto(Integer unread, String title, String chatId, String ownerId,
        String ownerUserId, String channelType, String supportBaseGroupId, List<String> memberIds,
        ChatSupportStatusDto supportCase) implements ChatDirectory.Chat {
    public ChatDirectoryEntryDto {
        if (memberIds != null) memberIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(memberIds));
    }
    public static ChatDirectoryEntryDto from(ChatDirectory.Chat chat) {
        return new ChatDirectoryEntryDto(chat.getUnread(), chat.getTitle(), chat.getChatId(), chat.getOwnerId(),
                chat.getOwnerUserId(), chat.getChannelType(), chat.getSupportBaseGroupId(), chat.getMemberIds(),
                chat.getSupportCase() == null ? null : new ChatSupportStatusDto(chat.getSupportCase().getStatus()));
    }
    @Override public Integer getUnread() { return unread; }
    @Override public String getTitle() { return title; }
    @Override public String getChatId() { return chatId; }
    @Override public String getOwnerId() { return ownerId; }
    @Override public String getOwnerUserId() { return ownerUserId; }
    @Override public String getChannelType() { return channelType; }
    @Override public String getSupportBaseGroupId() { return supportBaseGroupId; }
    @Override public List<String> getMemberIds() { return memberIds; }
    @Override public ChatSupportStatusDto getSupportCase() { return supportCase; }
}
