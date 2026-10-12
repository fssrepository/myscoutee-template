package com.raxim.myscoutee.domain.chat.api;



public interface ChatPresence {
    boolean isUserConnected(String chatId, String userId);
}
