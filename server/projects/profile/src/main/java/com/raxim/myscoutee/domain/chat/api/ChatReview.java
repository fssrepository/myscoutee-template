package com.raxim.myscoutee.domain.chat.api;
import java.util.List;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminChatMessageDto;
public interface ChatReview {
    ReportedChat reported(String chatId, String messageId, String reporterId, String targetId);
    record ReportedChat(String title, List<AdminChatMessageDto> messages) {}
    record ReportEvidence(String chatId, String messageId, String text) {}
    ReportEvidence reportEvidence(String reporter, String target, String chatId, String messageId);
}
