package com.raxim.myscoutee.microservices.transport.chat;

import java.util.function.Supplier;
import com.raxim.myscoutee.domain.chat.api.ChatDirectory;
import com.raxim.myscoutee.domain.chat.dto.ChatDirectoryEntryDto;
import com.raxim.myscoutee.domain.chat.dto.ChatUnreadVersionDto;
import com.raxim.myscoutee.microservices.transport.chat.ChatDirectoryProtocol.Error;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ChatDirectoryEndpoint {
    private final ChatDirectory chats;
    public ChatDirectoryEndpoint(ChatDirectory chats) { this.chats = chats; }
    @GetMapping(value = ChatDirectoryProtocol.ENTRY, produces = "application/json")
    public ResponseEntity<?> entry(@RequestParam(required = false) String ownerId, @RequestParam(required = false) String chatId) {
        return read(() -> chats.findByOwnerUserIdAndChatId(ownerId, chatId).map(ChatDirectoryEntryDto::from).orElse(null), ownerId, chatId);
    }
    @GetMapping(value = ChatDirectoryProtocol.ENTRIES, produces = "application/json")
    public ResponseEntity<?> entries(@RequestParam(required = false) String chatId) {
        return read(() -> chats.findByChatId(chatId).stream().map(ChatDirectoryEntryDto::from).toList(), chatId);
    }
    @GetMapping(value = ChatDirectoryProtocol.UNREAD, produces = "application/json")
    public ResponseEntity<?> unread(@RequestParam(required = false) String ownerId) {
        return read(() -> new ChatUnreadVersionDto(chats.unreadVersion(ownerId)), ownerId);
    }
    @GetMapping(value = ChatDirectoryProtocol.COUNTS, produces = "application/json")
    public ResponseEntity<?> counts(@RequestParam(required = false) String baseGroupId) {
        return read(() -> chats.supportCaseCounts(baseGroupId), baseGroupId);
    }
    private ResponseEntity<?> read(Supplier<?> read, String... parameters) {
        for (var value : parameters) if (value != null && value.length() > 512) return response(400, new Error("INVALID_REQUEST"));
        try {
            var result = read.get();
            return result == null ? response(404, new Error("NOT_FOUND")) : response(200, result);
        } catch (RuntimeException unavailable) { return response(503, new Error("CHAT_DIRECTORY_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
