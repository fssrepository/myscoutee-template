package com.raxim.myscoutee.domain.chat.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

@org.junit.jupiter.api.Tag("standard")
class ChatHandshakeInterceptorTest {

    @Test
    void adminJobSocketRetainsTheDatabaseSessionKind() {
        var request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost/api/admin/notifications/ws?userId=work-admin&sessionKind=demo"));
        Map<String, Object> attributes = new HashMap<>();
        assertTrue(new ChatHandshakeInterceptor().beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes));
        assertEquals("demo", attributes.get(ChatHandshakeInterceptor.SESSION_KIND_ATTRIBUTE));
        assertEquals("work-admin", attributes.get(ChatHandshakeInterceptor.USER_ID_ATTRIBUTE));
    }

    @Test
    void decodesEncodedChatIdBeforeStoringHandshakeAttributes() {
        ChatHandshakeInterceptor interceptor = new ChatHandshakeInterceptor();
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(
                "http://localhost/api/activities/chats/ws"
                        + "?chatId=manual-qa-checkpoint%3Av1%3Aevent-domain%3Aevent"
                        + "&userId=650000000000000000000063&sessionKind=demo"));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes);

        assertTrue(accepted);
        assertEquals(
                "manual-qa-checkpoint:v1:event-domain:event",
                attributes.get(ChatHandshakeInterceptor.CHAT_ID_ATTRIBUTE));
        assertEquals("650000000000000000000063", attributes.get(ChatHandshakeInterceptor.USER_ID_ATTRIBUTE));
        assertEquals("demo", attributes.get(ChatHandshakeInterceptor.SESSION_KIND_ATTRIBUTE));
    }
}
