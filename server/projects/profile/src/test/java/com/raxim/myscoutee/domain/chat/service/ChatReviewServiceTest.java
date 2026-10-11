package com.raxim.myscoutee.domain.chat.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Pageable;
import com.raxim.myscoutee.domain.chat.document.Chat;
import com.raxim.myscoutee.domain.chat.document.ChatMessage;
import com.raxim.myscoutee.domain.chat.repository.ChatsRepository;
import com.raxim.myscoutee.domain.chat.repository.ChatMessagesRepository;

@Tag("standard")
class ChatReviewServiceTest {
    private final ChatsRepository chats = mock(ChatsRepository.class);
    private final ChatMessagesRepository messages = mock(ChatMessagesRepository.class);
    private final ChatReviewService service = new ChatReviewService(chats, messages);
    @Test void missingReporterCopyOrTargetMembershipDoesNotExposeMessages() {
        assertThat(service.reported("chat", "message", "reporter", "target")).isNull();
        var chat = mock(Chat.class); when(chat.getMemberIds()).thenReturn(List.of("reporter", "someone-else"));
        when(chats.findByOwnerUserIdAndChatId("reporter", "chat")).thenReturn(Optional.of(chat));
        assertThat(service.reported("chat", "message", "reporter", "target")).isNull();
        verifyNoInteractions(messages);
    }
    @Test void wrongMessageAuthorDoesNotExposeChatHistory() {
        var chat = chat(); var message = message("message", "someone-else");
        when(messages.findByChatIdAndMessageId("chat", "message")).thenReturn(Optional.of(message));
        assertThat(service.reported("chat", "message", "reporter", "target")).isNull();
        verify(messages, never()).findByChatIdOrderBySentAtIsoDesc(anyString(), any(Pageable.class));
    }
    @Test void verifiedReportReturnsOnlyTheExistingFiftyMessageWindowInChronologicalOrder() {
        var chat = chat(); when(chat.getTitle()).thenReturn("Reported conversation");
        var newest = message("message", "target"); var older = message("older", "reporter");
        when(messages.findByChatIdAndMessageId("chat", "message")).thenReturn(Optional.of(newest));
        when(messages.findByChatIdOrderBySentAtIsoDesc("chat", org.springframework.data.domain.PageRequest.of(0, 50))).thenReturn(List.of(newest, older));
        var result = service.reported("chat", "message", "reporter", "target");
        assertThat(result.title()).isEqualTo("Reported conversation");
        assertThat(result.messages()).extracting(com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminChatMessageDto::id).containsExactly("older", "message");
    }
    private Chat chat() {
        var chat = mock(Chat.class); when(chat.getChatId()).thenReturn("chat"); when(chat.getMemberIds()).thenReturn(List.of("reporter", "target"));
        when(chats.findByOwnerUserIdAndChatId("reporter", "chat")).thenReturn(Optional.of(chat)); return chat;
    }
    private ChatMessage message(String id, String actor) {
        var message = mock(ChatMessage.class); var avatar = mock(ChatMessage.ChatAvatarRecord.class);
        when(message.getId()).thenReturn(id); when(message.getSenderAvatar()).thenReturn(avatar); when(avatar.getId()).thenReturn(actor); return message;
    }
}
