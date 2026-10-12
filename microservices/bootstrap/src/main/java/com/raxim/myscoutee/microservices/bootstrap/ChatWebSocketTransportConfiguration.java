package com.raxim.myscoutee.microservices.bootstrap;

import java.time.Clock;
import java.util.Map;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.microservices.transport.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.ObjectMapper;

/** Hooks for the existing chat handler, only in the independent chat assembly. */
@Configuration(proxyBeanMethods = false)
public class ChatWebSocketTransportConfiguration {
    private static final String AUTHORIZATION = ChatWebSocketTransportConfiguration.class.getName();

    @Bean("chatTransportHandshakeInterceptor")
    HandshakeInterceptor socketAuthentication(RemotePublicAuthorization owner, ActorDelegation.Verifier verifier,
            ObjectMapper mapper, @Value("${spring.application.name}") String service) {
        return new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler handler, Map<String,Object> attributes) {
                try {
                    if (!(request instanceof ServletServerHttpRequest servlet)) throw new IllegalArgumentException();
                    attributes.put(AUTHORIZATION, new SocketAuthorization(owner,
                            RemotePublicAuthorization.snapshot(servlet.getServletRequest()), verifier, mapper, service,
                            MongoSessionModeContext.currentMode(), SecurityContextHolder.getContext().getAuthentication(), Clock.systemUTC()));
                    return true;
                } catch (RuntimeException denied) { response.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
            }
            @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler handler, Exception exception) { }
        };
    }

    @Bean("chatTransportHandlerDecorator")
    WebSocketHandlerDecoratorFactory socketHandler() {
        return handler -> new WebSocketHandlerDecorator(handler) {
            private SocketAuthorization authorization(WebSocketSession session) {
                return (SocketAuthorization) session.getAttributes().get(AUTHORIZATION);
            }
            private WebSocketSession authenticated(WebSocketSession session) {
                return new WebSocketSessionDecorator(session) {
                    @Override public java.security.Principal getPrincipal() { return authorization(session).authentication(); }
                };
            }
            @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                super.afterConnectionEstablished(authenticated(session));
            }
            @Override public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                try { authorization(session).refreshIfNeeded(); }
                catch (RuntimeException denied) { session.close(CloseStatus.POLICY_VIOLATION); return; }
                super.handleMessage(authenticated(session), message);
            }
            @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                super.handleTransportError(authenticated(session), exception);
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                super.afterConnectionClosed(authenticated(session), status);
            }
        };
    }
}
