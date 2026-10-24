package com.raxim.myscoutee.microservices.transport.chat;

import java.io.IOException;
import java.util.List;
import com.raxim.myscoutee.domain.chat.api.ChatMediaVisibility;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import com.raxim.myscoutee.microservices.transport.common.MediaReferenceQueries;
import com.raxim.myscoutee.microservices.transport.chat.ChatMediaVisibilityProtocol.Error;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
public final class ChatMediaVisibilityEndpoint {
    private final ChatMediaVisibility chat;
    private final ObjectMapper mapper;
    public ChatMediaVisibilityEndpoint(ChatMediaVisibility chat, ObjectMapper mapper) { this.chat = chat; this.mapper = mapper; }
    @PostMapping(value = ChatMediaVisibilityProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> messages(HttpServletRequest request) throws IOException {
        List<String> urls;
        try { urls = MediaReferenceQueries.read(request, mapper); }
        catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new Error(invalid.code())); }
        try {
            var result = chat.messages(urls);
            return response(200, result);
        } catch (RuntimeException unavailable) { return response(503, new Error("CHAT_VISIBILITY_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
