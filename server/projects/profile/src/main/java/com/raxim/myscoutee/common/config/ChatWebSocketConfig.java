package com.raxim.myscoutee.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.chat.websocket.ChatHandshakeInterceptor;
import com.raxim.myscoutee.domain.chat.websocket.ChatWebSocketHandler;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final ConfigProperties configProperties;

    public ChatWebSocketConfig(
            ChatWebSocketHandler chatWebSocketHandler,
            ChatHandshakeInterceptor chatHandshakeInterceptor,
            ConfigProperties configProperties) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.chatHandshakeInterceptor = chatHandshakeInterceptor;
        this.configProperties = configProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/activities/chats/ws")
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns());
    }

    private String[] allowedOriginPatterns() {
        return configProperties.getWebsocketAllowedOriginPatternList().toArray(String[]::new);
    }
}
