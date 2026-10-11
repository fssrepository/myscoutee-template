package com.raxim.myscoutee.domain.chat.websocket;

import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;
import com.raxim.myscoutee.domain.accounts.api.AuthenticatedActor;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.api.AuthenticationMode;
import com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatSocketRequestDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatSocketEventDto;
import com.raxim.myscoutee.domain.chat.service.ChatService;
import com.raxim.myscoutee.domain.chat.service.ChatWebSocketSessionRegistry;
import com.raxim.myscoutee.domain.chat.service.ChatCallSignalingService;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final ChatWebSocketSessionRegistry sessionRegistry;
    private final AuthenticationMode configProperties;
    private final ChatCallSignalingService calls;

    public ChatWebSocketHandler(
            ObjectMapper objectMapper,
            ChatService chatService,
            ChatWebSocketSessionRegistry sessionRegistry,
            AuthenticationMode configProperties,
            ChatCallSignalingService calls) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.sessionRegistry = sessionRegistry;
        this.configProperties = configProperties;
        this.calls = calls;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        runWithSessionMode(session, () -> {
            String chatId = resolveChatId(session);
            String userId = resolveViewerUserId(session);
            if (!StringUtils.hasText(chatId) || !StringUtils.hasText(userId) || !chatService.hasChatAccess(chatId, userId)) {
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }
            sessionRegistry.register(chatId, userId, session);
            session.setTextMessageSizeLimit(65536);
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String chatId = resolveChatId(session);
        String userId = resolveViewerUserId(session);
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(userId)) {
            return;
        }
        tools.jackson.databind.JsonNode payload;
        try {
            payload = objectMapper.readTree(message.getPayload());
        } catch (Exception exception) {
            sendSocketError(session, chatId, null, null, "Invalid chat request.");
            return;
        }
        if (payload == null) return;
        if ("call".equals(payload.path("type").asText(""))) {
            try {
                if (message.getPayloadLength() > 65536) throw new IllegalArgumentException("Call signal too large.");
                var request = objectMapper.treeToValue(payload.path("call"), ChatCallSignalingService.Request.class);
                runWithSessionMode(session, () -> calls.handle(session, chatId, userId, request));
                return;
            } catch (Exception exception) {
                // Call errors are separate from durable chat-message acknowledgements.
                sessionRegistry.sendEvent(session, java.util.Map.of("type", "call", "chatId", chatId,
                        "kind", "error", "error", "Chat call request failed."));
                return;
            }
        }
        ChatSocketRequestDto request;
        try {
            request = objectMapper.readValue(message.getPayload(), ChatSocketRequestDto.class);
        } catch (Exception exception) {
            sendSocketError(session, chatId, null, null, "Invalid chat request.");
            return;
        }
        if (request == null) {
            return;
        }
        String type = request.type() == null ? "message" : request.type().trim();
        try {
            runWithSessionMode(session, () -> {
                if ("typing".equalsIgnoreCase(type)) {
                    chatService.sendTypingSignal(chatId, userId, request.typing());
                    return;
                }
                if ("read".equalsIgnoreCase(type)) {
                    chatService.markMessagesRead(chatId, userId, request.messageIds());
                    return;
                }
                if ("edit".equalsIgnoreCase(type)) {
                    ChatMessageDto saved = chatService.editMessage(chatId, userId, request.messageId(), request.text());
                    sessionRegistry.sendAck(session, chatId, saved);
                    return;
                }
                if ("delete".equalsIgnoreCase(type)) {
                    ChatMessageDto saved = chatService.deleteMessage(chatId, userId, request.messageId());
                    sessionRegistry.sendAck(session, chatId, saved);
                    return;
                }
                if ("pin".equalsIgnoreCase(type)) {
                    ChatMessageDto saved = chatService.pinMessage(chatId, userId, request.messageId(), request.pinned());
                    sessionRegistry.sendAck(session, chatId, saved);
                    return;
                }
                if ("reaction".equalsIgnoreCase(type)) {
                    ChatMessageDto saved = chatService.reactToMessage(chatId, userId, request.messageId(), request.emoji());
                    sessionRegistry.sendAck(session, chatId, saved);
                    return;
                }
                if ("attachments".equalsIgnoreCase(type)) {
                    ChatMessageDto saved = chatService.updateMessageAttachments(chatId, userId, request.messageId(), request.attachments());
                    sessionRegistry.sendAck(session, chatId, saved);
                    return;
                }
                if (!type.isEmpty() && !"message".equalsIgnoreCase(type)) {
                    return;
                }
                if (!StringUtils.hasText(request.text()) && (request.attachments() == null || request.attachments().isEmpty())) {
                    return;
                }
                ChatMessageDto saved = chatService.sendMessage(chatId, userId, request.text(), request.clientId(), request.replyTo(), request.attachments());
                sessionRegistry.sendAck(session, chatId, saved);
            });
        } catch (Exception exception) {
            LOGGER.warn("Unable to store websocket chat update. chatId={}, userId={}, type={}, messageId={}, clientId={}",
                    chatId, userId, type, request.messageId(), request.clientId(), exception);
            sendSocketError(session, chatId, request.messageId(), request.clientId(), socketErrorMessage(exception));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
        calls.disconnected(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sessionRegistry.unregister(session);
        calls.disconnected(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private String resolveChatId(WebSocketSession session) {
        Object value = session.getAttributes().get(ChatHandshakeInterceptor.CHAT_ID_ATTRIBUTE);
        return value instanceof String chatId ? chatId : null;
    }

    private String resolveRequestedUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(ChatHandshakeInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof String userId ? userId : null;
    }

    private MongoSessionMode resolveSessionMode(WebSocketSession session) {
        Object value = session.getAttributes().get(ChatHandshakeInterceptor.SESSION_KIND_ATTRIBUTE);
        return value instanceof String sessionKind && DemoSessionRequestSupport.isDemoSessionKind(sessionKind)
                ? MongoSessionMode.DEMO
                : MongoSessionMode.REAL;
    }

    private void runWithSessionMode(WebSocketSession session, SocketOperation operation) throws Exception {
        MongoSessionMode previousMode = MongoSessionModeContext.currentMode();
        var previousSecurityContext = SecurityContextHolder.getContext();
        var socketSecurityContext = SecurityContextHolder.createEmptyContext();
        if (session.getPrincipal() instanceof Authentication authentication) {
            socketSecurityContext.setAuthentication(authentication);
        }
        MongoSessionModeContext.set(resolveSessionMode(session));
        SecurityContextHolder.setContext(socketSecurityContext);
        try {
            operation.run();
        } finally {
            SecurityContextHolder.setContext(previousSecurityContext);
            MongoSessionModeContext.set(previousMode);
        }
    }

    private String resolveViewerUserId(WebSocketSession session) {
        AuthenticatedActor principal = resolvePrincipal(session);
        if (principal != null) {
            ProfileIdentityDto user = principal.getProfileIdentity();
            String principalUserId = user != null && StringUtils.hasText(user.userId())
                    ? user.userId().trim()
                    : (principal.getUserId() == null ? "" : principal.getUserId().trim());
            if (StringUtils.hasText(principalUserId)) {
                return principalUserId;
            }
        }
        if (configProperties.isFirebaseEnabled() && resolveSessionMode(session) != MongoSessionMode.DEMO) {
            return null;
        }
        return resolveRequestedUserId(session);
    }

    private AuthenticatedActor resolvePrincipal(WebSocketSession session) {
        if (!(session.getPrincipal() instanceof org.springframework.security.core.Authentication authentication)) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedActor firebasePrincipal ? firebasePrincipal : null;
    }

    private void sendSocketError(
            WebSocketSession session,
            String chatId,
            String messageId,
            String clientId,
            String error) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new ChatSocketEventDto(
                    "error",
                    chatId,
                    null,
                    null,
                    null,
                    messageId,
                    clientId,
                    error))));
        } catch (Exception ignored) {
            // The client still has its optimistic timeout if the error frame cannot be sent.
        }
    }

    private String socketErrorMessage(Exception exception) {
        if (exception instanceof ResponseStatusException responseStatusException) {
            String reason = responseStatusException.getReason();
            if (StringUtils.hasText(reason)) {
                return reason.trim();
            }
        }
        String message = exception == null ? "" : exception.getMessage();
        return StringUtils.hasText(message) ? message.trim() : "Unable to store chat update.";
    }

    @FunctionalInterface
    private interface SocketOperation {
        void run() throws Exception;
    }
}
