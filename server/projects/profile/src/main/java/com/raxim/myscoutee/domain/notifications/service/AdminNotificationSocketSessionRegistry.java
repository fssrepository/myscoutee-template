package com.raxim.myscoutee.domain.notifications.service;

import java.util.List;
import com.raxim.myscoutee.domain.platform.api.JobGroupContext;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.raxim.myscoutee.domain.notifications.document.NotificationRuleConfig;

import tools.jackson.databind.ObjectMapper;

@Component
public class AdminNotificationSocketSessionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminNotificationSocketSessionRegistry.class);

    private final ObjectMapper objectMapper;
    public static final String GROUP_ATTRIBUTE = "jobBaseGroupId";
    public static final String DATABASE_ATTRIBUTE = "jobDatabaseMode";
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public AdminNotificationSocketSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session) {
        if (session != null && session.isOpen()) {
            sessions.add(session);
        }
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void broadcastRuleRuntime(NotificationRuleConfig rule) {
        if (rule == null || sessions.isEmpty()) {
            return;
        }
        JobGroupContext.run(rule.getBaseGroupId(), () -> broadcastRuleRuntime(
                rule.getRuleKey(),
                rule.getRunState(),
                rule.getRunHistory(),
                rule.getUpdatedDate(),
                rule.getUpdatedUser()));
    }

    public void broadcastRuleRuntime(
            String ruleKey,
            NotificationRuleConfig.RunState runState,
            List<NotificationRuleConfig.RunHistoryEntry> runHistory,
            String updatedDate,
            String updatedUser) {
        if (sessions.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", "rule-runtime",
                "ruleKey", safeText(ruleKey),
                "runState", runState == null ? new NotificationRuleConfig.RunState() : runState,
                "runHistory", runHistory == null ? List.of() : runHistory,
                "updatedDate", safeText(updatedDate),
                "updatedUser", safeText(updatedUser));
        broadcast(payload);
    }

    private void broadcast(Object payload) {
        String encoded;
        try {
            encoded = objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            LOGGER.warn("Unable to encode admin notification socket event.", exception);
            return;
        }
        TextMessage message = new TextMessage(encoded);
        for (WebSocketSession session : Set.copyOf(sessions)) {
            if (session == null || !session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            String group = (String) session.getAttributes().get(GROUP_ATTRIBUTE);
            String database = (String) session.getAttributes().get(DATABASE_ATTRIBUTE);
            if (!java.util.Objects.equals(group, JobGroupContext.groupId())
                    || !MongoSessionModeContext.currentMode().name().equals(database)) continue;
            try {
                session.sendMessage(message);
            } catch (Exception exception) {
                sessions.remove(session);
                LOGGER.warn("Unable to send admin notification socket event to session {}.", session.getId(), exception);
            }
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
