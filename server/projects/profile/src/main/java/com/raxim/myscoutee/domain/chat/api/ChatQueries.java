package com.raxim.myscoutee.domain.chat.api;
import java.util.*;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.*;

public interface ChatQueries {
    ChatDto queryChatById(String chatId, String requestedUserId);
    ChatPageResponseDto queryChatPage(
            String requestedUserId,
            String contextFilterValue,
            String sortValue,
            String sortDirectionValue,
            String secondaryFilterValue,
            String cursor,
            Integer limitValue,
            String rangeStartIso,
            String rangeEndIso,
            Boolean adminServiceOnly,
            String supportCaseFilterValue);
    ChatMessagesPageResponseDto queryMessagesPage(
            String chatId,
            String requestedUserId,
            Integer limit,
            String cursor);
    ChatMessagesPageResponseDto queryMessagesPage(
            String chatId, String requestedUserId, Integer limit, String cursor,
            String targetMessageId);
    ChatMessagesPageResponseDto queryMessagesPage(
            String chatId, String requestedUserId, Integer limit, String cursor,
            String targetMessageId, boolean markViewed);
}
