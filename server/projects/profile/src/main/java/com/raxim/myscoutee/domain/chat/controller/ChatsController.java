package com.raxim.myscoutee.domain.chat.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDeviceDeleteRequestDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDeviceUpsertRequestDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatHeaderSyncResponseDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessageMutationRequestDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMessagesPageResponseDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatMembersPageResponseDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatPageResponseDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatPostMessageRequestDto;
import com.raxim.myscoutee.domain.chat.dto.ChatDtos.ChatDto;
import com.raxim.myscoutee.domain.access.api.AccessPolicyKeys;
import com.raxim.myscoutee.domain.access.api.RequiresPolicy;
import com.raxim.myscoutee.domain.chat.service.ChatService;

@RestController
@RequestMapping("/activities/chats")
public class ChatsController {
    private final ChatService chatService;

    public ChatsController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatDto> queryChats(@RequestParam("userId") String userId) {
        return chatService.queryChats(userId);
    }

    @GetMapping("/{chatId}/header-sync")
    public ChatHeaderSyncResponseDto syncChatHeader(
            @PathVariable("chatId") String chatId,
            @RequestParam("userId") String userId,
            @RequestParam(value = "knownRevision", required = false) Long knownRevision) {
        return chatService.syncChatHeader(chatId, userId, knownRevision);
    }

    @GetMapping("/page")
    public ChatPageResponseDto queryChatPage(
            @RequestParam("userId") String userId,
            @RequestParam(value = "contextFilter", required = false) String contextFilter,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "sortDirection", required = false) String sortDirection,
            @RequestParam(value = "secondaryFilter", required = false) String secondaryFilter,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "rangeStartIso", required = false) String rangeStartIso,
            @RequestParam(value = "rangeEndIso", required = false) String rangeEndIso,
            @RequestParam(value = "adminServiceOnly", required = false) Boolean adminServiceOnly,
            @RequestParam(value = "supportCaseFilter", required = false) String supportCaseFilter) {
        return chatService.queryChatPage(
                userId,
                contextFilter,
                sort,
                sortDirection,
                secondaryFilter,
                cursor,
                limit,
                rangeStartIso,
                rangeEndIso,
                adminServiceOnly,
                supportCaseFilter);
    }

    @GetMapping("/by-id/{chatId}")
    public ChatDto queryChatById(
            @PathVariable("chatId") String chatId,
            @RequestParam("userId") String userId) {
        return chatService.queryChatById(chatId, userId);
    }

    public record ContactChatRequest(String targetUserId) {}

    @PostMapping("/contact")
    public ChatDto ensureContactChat(@RequestBody ContactChatRequest request,
            @RequestParam(value = "userId", required = false) String userId) {
        return chatService.ensureContactChat(userId, request == null ? null : request.targetUserId());
    }

    @GetMapping("/{chatId}/messages")
    public ChatMessagesPageResponseDto queryMessagesPage(
            @PathVariable("chatId") String chatId,
            @RequestParam("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "targetMessageId", required = false) String targetMessageId) {
        return chatService.queryMessagesPage(chatId, userId, limit, cursor, targetMessageId);
    }

    @GetMapping("/{chatId}/members/page")
    public ChatMembersPageResponseDto queryMembersPage(
            @PathVariable("chatId") String chatId,
            @RequestParam("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "pendingOnly", required = false) Boolean pendingOnly) {
        return chatService.queryMembersPage(chatId, userId, limit, cursor, pendingOnly);
    }

    @PostMapping("/{chatId}/messages")
    @RequiresPolicy(
            key = AccessPolicyKeys.CHAT_MESSAGE_CREATE,
            subject = "#userId ?: #actorUserId",
            resourceId = "#chatId",
            skipWhenAuthenticationOptional = true)
    public ChatMessageDto sendMessage(
            @PathVariable("chatId") String chatId,
            @RequestBody ChatPostMessageRequestDto request,
            @RequestParam(value = "userId", required = false) String userId) {
        if (request == null) {
            return null;
        }
        return chatService.sendMessage(chatId, userId, request.text(), null, request.replyTo(), request.attachments());
    }

    @PostMapping("/{chatId}/messages/{messageId}/edit")
    @RequiresPolicy(
            key = AccessPolicyKeys.CHAT_MESSAGE_EDIT,
            subject = "#userId ?: #actorUserId",
            resourceId = "#chatId",
            skipWhenAuthenticationOptional = true)
    public ChatMessageDto editMessage(
            @PathVariable("chatId") String chatId,
            @PathVariable("messageId") String messageId,
            @RequestBody ChatMessageMutationRequestDto request,
            @RequestParam(value = "userId", required = false) String userId) {
        return chatService.editMessage(chatId, userId, messageId, request == null ? "" : request.text());
    }

    @PostMapping("/{chatId}/messages/{messageId}/delete")
    @RequiresPolicy(
            key = AccessPolicyKeys.CHAT_MESSAGE_DELETE,
            subject = "#userId ?: #actorUserId",
            resourceId = "#chatId",
            skipWhenAuthenticationOptional = true)
    public ChatMessageDto deleteMessage(
            @PathVariable("chatId") String chatId,
            @PathVariable("messageId") String messageId,
            @RequestParam(value = "userId", required = false) String userId) {
        return chatService.deleteMessage(chatId, userId, messageId);
    }

    @PostMapping("/{chatId}/messages/{messageId}/pin")
    @RequiresPolicy(
            key = AccessPolicyKeys.CHAT_MESSAGE_PIN,
            subject = "#userId ?: #actorUserId",
            resourceId = "#chatId",
            skipWhenAuthenticationOptional = true)
    public ChatMessageDto pinMessage(
            @PathVariable("chatId") String chatId,
            @PathVariable("messageId") String messageId,
            @RequestBody ChatMessageMutationRequestDto request,
            @RequestParam(value = "userId", required = false) String userId) {
        return chatService.pinMessage(chatId, userId, messageId, request != null && Boolean.TRUE.equals(request.pinned()));
    }

    @PostMapping("/{chatId}/messages/{messageId}/reaction")
    @RequiresPolicy(
            key = AccessPolicyKeys.CHAT_REACTION_MANAGE,
            subject = "#userId ?: #actorUserId",
            resourceId = "#chatId",
            skipWhenAuthenticationOptional = true)
    public ChatMessageDto reactToMessage(
            @PathVariable("chatId") String chatId,
            @PathVariable("messageId") String messageId,
            @RequestBody ChatMessageMutationRequestDto request,
            @RequestParam(value = "userId", required = false) String userId) {
        return chatService.reactToMessage(chatId, userId, messageId, request == null ? "" : request.emoji());
    }

    @PostMapping("/{chatId}/messages/{messageId}/attachments")
    @RequiresPolicy(
            key = AccessPolicyKeys.CHAT_ATTACHMENT_ADD,
            subject = "#userId ?: #actorUserId",
            resourceId = "#chatId",
            skipWhenAuthenticationOptional = true)
    public ChatMessageDto updateMessageAttachments(
            @PathVariable("chatId") String chatId,
            @PathVariable("messageId") String messageId,
            @RequestBody ChatMessageMutationRequestDto request,
            @RequestParam(value = "userId", required = false) String userId) {
        return chatService.updateMessageAttachments(chatId, userId, messageId, request == null ? null : request.attachments());
    }

    @PostMapping("/devices")
    public void upsertDevice(
            @RequestBody ChatDeviceUpsertRequestDto request,
            @RequestParam(value = "userId", required = false) String userId) {
        chatService.upsertDevice(request == null
                ? null
                : new ChatDeviceUpsertRequestDto(
                        request.userId() == null ? userId : request.userId(),
                        request.deviceId(),
                        request.platform(),
                        request.firebaseToken(),
                        request.notificationsEnabled()));
    }

    @DeleteMapping("/devices")
    public void deleteDevice(
            @RequestBody(required = false) ChatDeviceDeleteRequestDto request,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "deviceId", required = false) String deviceId,
            @RequestParam(value = "firebaseToken", required = false) String firebaseToken) {
        ChatDeviceDeleteRequestDto resolvedRequest = request != null
                ? new ChatDeviceDeleteRequestDto(
                        request.userId() == null ? userId : request.userId(),
                        request.deviceId(),
                        request.firebaseToken())
                : new ChatDeviceDeleteRequestDto(userId, deviceId, firebaseToken);
        chatService.deleteDevice(resolvedRequest);
    }


}
