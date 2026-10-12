package com.raxim.myscoutee.common.config;

import static org.mockito.Mockito.*;

import com.raxim.myscoutee.domain.chat.websocket.ChatHandshakeInterceptor;
import com.raxim.myscoutee.domain.chat.websocket.ChatWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@org.junit.jupiter.api.Tag("standard")
class ChatWebSocketConfigTest {
    @Test
    void preservesChatHandlerAndExplicitOriginPolicyWithoutPlatformImplementation() {
        var chat = mock(ChatWebSocketHandler.class);
        var interceptor = mock(ChatHandshakeInterceptor.class);
        var registry = mock(WebSocketHandlerRegistry.class);
        var chatRegistration = mock(WebSocketHandlerRegistration.class, RETURNS_SELF);
        when(registry.addHandler(chat, "/activities/chats/ws")).thenReturn(chatRegistration);

        new ChatWebSocketConfig(chat, interceptor,
                " https://app.example ;http://localhost:18080,,https://app.example ")
                .registerWebSocketHandlers(registry);

        for (var registration : new WebSocketHandlerRegistration[]{chatRegistration}) {
            verify(registration).addInterceptors(interceptor);
            verify(registration).setAllowedOriginPatterns("https://app.example", "http://localhost:18080");
        }
        verifyNoMoreInteractions(chatRegistration);
    }
}
