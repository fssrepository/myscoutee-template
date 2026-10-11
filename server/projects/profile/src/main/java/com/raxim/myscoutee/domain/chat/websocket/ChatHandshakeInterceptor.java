package com.raxim.myscoutee.domain.chat.websocket;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    public static final String CHAT_ID_ATTRIBUTE = "chatId";
    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String SESSION_KIND_ATTRIBUTE = com.raxim.myscoutee.domain.chat.api.ChatSessionAttributes.SESSION_KIND;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        var queryParams = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String sessionKind = decodeQueryParam(queryParams.getFirst("sessionKind"));
        if (!StringUtils.hasText(sessionKind)) {
            sessionKind = request.getHeaders().getFirst(DemoSessionRequestSupport.SESSION_KIND_HEADER);
        }
        if (StringUtils.hasText(sessionKind)) {
            attributes.put(SESSION_KIND_ATTRIBUTE, sessionKind.trim());
        }
        String chatId = decodeQueryParam(queryParams.getFirst("chatId"));
        String path = request.getURI() == null ? "" : request.getURI().getPath();
        if (path != null && path.endsWith("/admin/notifications/ws")) {
            String userId = decodeQueryParam(queryParams.getFirst("userId"));
            if (StringUtils.hasText(userId)) {
                attributes.put(USER_ID_ATTRIBUTE, userId.trim());
            }
            return true;
        }
        if (!StringUtils.hasText(chatId)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        attributes.put(CHAT_ID_ATTRIBUTE, chatId.trim());
        String userId = decodeQueryParam(queryParams.getFirst("userId"));
        if (StringUtils.hasText(userId)) {
            attributes.put(USER_ID_ATTRIBUTE, userId.trim());
        }
        return true;
    }

    private String decodeQueryParam(String value) {
        return value == null ? null : UriUtils.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
