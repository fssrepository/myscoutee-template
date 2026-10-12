package com.raxim.myscoutee.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import com.raxim.myscoutee.domain.chat.websocket.ChatHandshakeInterceptor;
import com.raxim.myscoutee.domain.chat.websocket.ChatWebSocketHandler;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final String[] allowedOrigins;
    private org.springframework.web.socket.server.HandshakeInterceptor transportInterceptor;
    private org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory transportDecorator;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void transportInterceptor(@org.springframework.beans.factory.annotation.Qualifier("chatTransportHandshakeInterceptor")
            org.springframework.web.socket.server.HandshakeInterceptor value) { transportInterceptor = value; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void transportDecorator(@org.springframework.beans.factory.annotation.Qualifier("chatTransportHandlerDecorator")
            org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory value) { transportDecorator = value; }


    public ChatWebSocketConfig(
            ChatWebSocketHandler chatWebSocketHandler,
            ChatHandshakeInterceptor chatHandshakeInterceptor,
            @Value("${config.websocket-allowed-origin-patterns:http://localhost,http://localhost:4200,https://localhost,https://localhost:4200}")
            String allowedOriginPatterns) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.chatHandshakeInterceptor = chatHandshakeInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOriginPatterns.split("[,;]"))
                .map(String::trim).filter(StringUtils::hasText).distinct().toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        register(registry, chatWebSocketHandler, "/activities/chats/ws");
    }

    private void register(WebSocketHandlerRegistry registry, org.springframework.web.socket.WebSocketHandler handler, String path) {
        var registration = registry.addHandler(transportDecorator == null ? handler : transportDecorator.decorate(handler), path)
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns());
        if (transportInterceptor != null) registration.addInterceptors(transportInterceptor);
    }

    private String[] allowedOriginPatterns() {
        return allowedOrigins;
    }
}
