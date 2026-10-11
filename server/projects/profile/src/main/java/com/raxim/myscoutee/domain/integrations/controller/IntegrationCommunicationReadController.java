package com.raxim.myscoutee.domain.integrations.controller;

import static com.raxim.myscoutee.domain.integrations.controller.IntegrationReadSupport.*;

import com.raxim.myscoutee.domain.chat.api.ChatQueries;
import com.raxim.myscoutee.domain.notifications.api.NotificationReads;
import com.raxim.myscoutee.domain.integrations.service.IntegrationCapabilitiesService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Read/Full-pack chat and notification routes, using the authenticated integration's profile. */
@RestController
@RequestMapping("/integrations/v1")
public class IntegrationCommunicationReadController {
    private final IntegrationCapabilitiesService access;
    private final ChatQueries chats;
    private final NotificationReads notifications;

    public IntegrationCommunicationReadController(IntegrationCapabilitiesService access,
            ChatQueries chats, NotificationReads notifications) {
        this.access = access;
        this.chats = chats;
        this.notifications = notifications;
    }

    @GetMapping("/chats")
    public Object listChats(Authentication authentication, @RequestParam(required = false) String context, @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor) {
        String actor = access.requireRead(authentication, null);
        pageLimit(limit);
        return chats.queryChatPage(actor, context, "recent", null, null, cursor, limit, null, null, false, null);
    }

    @GetMapping("/chats/{id}")
    public Object chatDetails(Authentication authentication, @PathVariable String id) {
        String actor = access.requireRead(authentication, null);
        return found(chats.queryChatById(id, actor));
    }

    @GetMapping("/chats/{id}/messages")
    public Object chatMessages(Authentication authentication, @PathVariable String id, @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor) {
        String actor = access.requireRead(authentication, null);
        pageLimit(limit);
        return chats.queryMessagesPage(id, actor, limit, cursor, null, false);
    }

    @GetMapping("/notifications")
    public Object listNotifications(Authentication authentication, @RequestParam(required = false) String bucket, @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor) {
        String actor = access.requireRead(authentication, null);
        pageLimit(limit);
        return notifications.queryPage(actor, bucket, limit, cursor, null);
    }

}
