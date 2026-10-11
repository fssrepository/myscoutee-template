package com.raxim.myscoutee.domain.chat.service;

import com.raxim.myscoutee.domain.accounts.service.HomeUserProfileService;
import com.raxim.myscoutee.domain.accounts.service.UserActivityCountersService;
import com.raxim.myscoutee.domain.administration.api.AdministratorClassification;
import com.raxim.myscoutee.domain.administration.service.AdminTelemetryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;

import com.raxim.myscoutee.domain.chat.document.Chat;
import com.raxim.myscoutee.domain.chat.document.ContactChatAccess;
import com.raxim.myscoutee.domain.chat.document.ChatMessage;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatAvatarRecord;
import com.raxim.myscoutee.domain.chat.document.ChatMessage.ChatReadAvatarRecord;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserChatCountersDto;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserMenuCountersDto;
import com.raxim.myscoutee.domain.chat.repository.ChatMessagesRepository;
import com.raxim.myscoutee.domain.chat.repository.ChatsRepository;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
import com.raxim.myscoutee.domain.access.security.AccessControlService;
import com.raxim.myscoutee.domain.access.api.AccessPolicyKeys;
import com.raxim.myscoutee.domain.chat.mapper.ChatMapper;
import com.raxim.myscoutee.domain.chat.mapper.ChatMessageMapper;
import com.raxim.myscoutee.domain.notifications.service.UserNotificationService;

@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Tag("standard")
class ChatServiceTest {
    @Mock
    private ChatsRepository chatsRepository;
    @Mock
    private ChatMessagesRepository chatMessagesRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private BulkOperations bulkOperations;
    @Mock
    private BulkOperations userCounterBulkOperations;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private HomeUserProfileService homeUserProfileService;
    @Mock
    private com.raxim.myscoutee.domain.accounts.api.SelectedProfile selectedProfile;
    @Mock
    private UserActivityCountersService userActivityCountersService;
    @Mock
    private ChatStreamPublisher chatStreamPublisher;
    @Mock
    private ChatPushNotificationService chatPushNotificationService;
    @Mock
    private UserNotificationService userNotificationService;
    @Mock
    private AdminTelemetryService adminTelemetryService;
    @Mock
    private AdministratorClassification adminMenuCounterService;
    @Mock
    private AccessControlService accessControlService;

    private ChatService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(accessControlService.resolveChatActorUserId(any(), any()))
                .thenAnswer(call -> call.getArgument(1));
        service = new ChatService(chatsRepository, chatMessagesRepository, mongoTemplate,
                profileDirectory(), homeUserProfileService, userActivityCountersService,
                chatStreamPublisher, chatPushNotificationService, userNotificationService,
                adminTelemetryService, adminMenuCounterService, accessControlService,
                new ChatMapper(), new ChatMessageMapper(profileDirectory()));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "chatCounters",
                new com.raxim.myscoutee.domain.accounts.service.AccountChatCounterService(mongoTemplate));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "selectedProfile", selectedProfile);
    }

    @AfterEach
    void tearDown() { service.shutdownChatWriteExecutor(); }

    @Test
    void retriedShareReturnsThePersistedMessageWithoutAnotherUnreadIncrement() {
        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(accessControlService.canCurrentActorCreateChatMessage("viewer", "chat-1")).thenReturn(true);
        when(chatsRepository.findByChatId("chat-1")).thenReturn(List.of(chat("chat-1", "viewer", 0)));
        when(usersRepository.findByUserId("viewer")).thenReturn(Optional.of(user("viewer", "Viewer", "V")));
        var original = message("persisted", "viewer", "Viewer", "2026-09-25T10:00:00Z");
        when(chatMessagesRepository.insert(any(ChatMessage.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("same client command"));
        when(chatMessagesRepository.findByChatIdAndMessageId(eq("chat-1"), anyString())).thenReturn(Optional.of(original));
        var response = service.sendMessage("chat-1", "viewer", "shared message", "share:stable-command");
        assertThat(response.id()).isEqualTo("persisted");
        verify(chatMessagesRepository, never()).save(any(ChatMessage.class));
        verify(chatPushNotificationService, never()).notifyRecipients(anyList(), any());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Users.class));
    }

    @Test
    void queryMessagesPageReusesUserLookupCacheForRepeatedParticipants() {
        when(usersRepository.findByUserIdIn(anyCollection())).thenReturn(List.of(user("sender", "Sender User", "SU"), user("viewer", "Viewer User", "VU")));
        Chat viewerChat = chat("chat-1", "viewer", 0);
        Chat senderChat = chat("chat-1", "sender", 0);
        List<ChatMessage> messages = List.of(
                message("m1", "sender", "viewer", "2026-05-26T08:00:01Z"),
                message("m2", "sender", "viewer", "2026-05-26T08:00:02Z"),
                message("m3", "sender", "viewer", "2026-05-26T08:00:03Z"));

        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(chatsRepository.findByChatId("chat-1")).thenReturn(List.of(viewerChat, senderChat));
        when(chatMessagesRepository.countByChatId("chat-1")).thenReturn(3L);
        when(chatMessagesRepository.findByChatIdOrderBySentAtIsoDesc(anyString(), any(Pageable.class))).thenReturn(messages);



        var result = service.queryMessagesPage("chat-1", "viewer", 10, null);

        assertThat(result.items()).hasSize(3);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.read()).isNull();
        verify(usersRepository).findByUserIdIn(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of("sender", "viewer"))));
        verify(usersRepository, never()).findByUserId(anyString());
        verify(chatMessagesRepository, never()).saveAll(anyList());
        verify(chatsRepository, never()).save(any(Chat.class));
    }

    @Test
    void syncChatHeaderReturnsTheCurrentPersistedListRowState() {
        Chat viewerChat = chat("chat-1", "viewer", 3, "2026-08-30T05:15:00Z", "contact");
        viewerChat.setRevision(42L);
        viewerChat.setOwnerStatus("A");
        viewerChat.setLastMessage("Latest group message");
        viewerChat.setLastSenderId("sender");
        viewerChat.setContextStartAtIso("2026-08-30T06:00:00Z");
        viewerChat.setContextEndAtIso("2026-08-30T07:30:00Z");

        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(homeUserProfileService.queryMenuCounters("viewer")).thenReturn(new UserMenuCountersDto(3, new UserChatCountersDto(3, 0, null, 3), 0));
        when(chatsRepository.findByOwnerUserIdAndChatId("viewer", "chat-1"))
                .thenReturn(Optional.of(viewerChat));

        var unchanged = service.syncChatHeader("chat-1", "viewer", 42L);
        var changed = service.syncChatHeader("chat-1", "viewer", 41L);

        assertThat(unchanged.changed()).isFalse();
        assertThat(changed.changed()).isTrue();
        assertThat(changed.revision()).isEqualTo(42L);
        assertThat(changed.ownerStatus()).isEqualTo("A");
        assertThat(changed.unread()).isEqualTo(3);
        assertThat(changed.lastMessage()).isEqualTo("Latest group message");
        assertThat(changed.lastSenderId()).isEqualTo("sender");
        assertThat(changed.dateIso()).isEqualTo("2026-08-30T05:15:00Z");
        assertThat(changed.contextStartAtIso()).isEqualTo("2026-08-30T06:00:00Z");
        assertThat(changed.contextEndAtIso()).isEqualTo("2026-08-30T07:30:00Z");
        assertThat(changed.chats()).isEqualTo(3);
        assertThat(changed.chatCounters().contacts()).isEqualTo(3);
    }

    @Test
    void initialSystemMessageIsStoredOnceAndIncrementsEveryParticipantChatCounterOnce() {
        Chat first = chat("random-room-1", "user-1", 0, "2026-05-26T08:00:00Z", "contact");
        Chat second = chat("random-room-1", "user-2", 0, "2026-05-26T08:00:00Z", "contact");
        first.setMemberIds(List.of("user-1", "user-2"));
        second.setMemberIds(List.of("user-1", "user-2"));
        AtomicReference<ChatMessage> inserted = new AtomicReference<>();

        when(chatsRepository.findByChatId("random-room-1")).thenReturn(List.of(first, second));
        when(chatMessagesRepository.findByChatIdAndMessageId(
                "random-room-1",
                "system:random-room-welcome"))
                .thenAnswer(invocation -> Optional.ofNullable(inserted.get()));
        when(chatMessagesRepository.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            inserted.set(message);
            return message;
        });
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Chat.class))
                .thenReturn(bulkOperations);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Users.class))
                .thenReturn(userCounterBulkOperations);

        var firstResult = service.ensureInitialSystemMessage(
                "random-room-1",
                "Welcome to this automatically generated room.",
                "random-room-welcome");
        var repeatedResult = service.ensureInitialSystemMessage(
                "random-room-1",
                "Welcome to this automatically generated room.",
                "random-room-welcome");

        assertThat(firstResult).isNotNull();
        assertThat(repeatedResult).isNotNull();
        assertThat(inserted.get()).satisfies(message -> {
            assertThat(message.getId()).isEqualTo("system:random-room-welcome");
            assertThat(message.getSender()).isEqualTo("Templates System");
            assertThat(message.getText()).contains("automatically generated room");
        });
        verify(chatMessagesRepository, times(1)).insert(any(ChatMessage.class));
        ArgumentCaptor<Update> chatUpdates = ArgumentCaptor.forClass(Update.class);
        verify(bulkOperations, times(2)).updateOne(any(Query.class), chatUpdates.capture());
        assertThat(chatUpdates.getAllValues()).allSatisfy(update -> {
            Document increments = update.getUpdateObject().get("$inc", Document.class);
            assertThat(((Number) increments.get("revision")).longValue()).isEqualTo(1L);
        });
        ArgumentCaptor<Update> counterUpdates = ArgumentCaptor.forClass(Update.class);
        verify(userCounterBulkOperations, times(2)).updateOne(
                any(Query.class),
                counterUpdates.capture());
        verify(userCounterBulkOperations).execute();
        assertThat(counterUpdates.getAllValues()).allSatisfy(update -> {
            Document increments = update.getUpdateObject().get("$inc", Document.class);
            assertThat(((Number) increments.get("activities.chats")).intValue()).isEqualTo(1);
            assertThat(((Number) increments.get("activities.chat.all")).intValue()).isEqualTo(1);
            assertThat(((Number) increments.get("activities.chat.contacts")).intValue()).isEqualTo(1);
        });
    }

    @Test
    void openingFirstMessagePageClearsTheWholePersistedUnreadCount() {
        when(usersRepository.findByUserIdIn(anyCollection())).thenReturn(List.of(user("sender", "Sender User", "SU")));
        Chat viewerChat = chat("chat-1", "viewer", 4, "2026-05-26T08:00:00Z", "contact");
        Chat senderChat = chat("chat-1", "sender", 0);
        ChatMessage unreadMessage = message("m1", "sender", "someone-else", "2026-05-26T08:00:01Z");

        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(chatsRepository.findByChatId("chat-1")).thenReturn(List.of(viewerChat, senderChat));
        when(chatMessagesRepository.countByChatId("chat-1")).thenReturn(1L);
        when(chatMessagesRepository.findByChatIdOrderBySentAtIsoDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of(unreadMessage));

        Users viewer = user("viewer", "Viewer User", "VU");
        viewer.setImages(List.of("/images/profiles/viewer/small.webp"));
        when(usersRepository.findByUserId("viewer")).thenReturn(Optional.of(viewer));
        when(mongoTemplate.updateMulti(any(Query.class), any(), eq(ChatMessage.class)))
                .thenReturn(UpdateResult.acknowledged(4L, 4L, null));
        when(chatsRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.queryMessagesPage("chat-1", "viewer", 10, null);

        assertThat(result.read()).isNotNull();
        assertThat(result.read().unread()).isZero();
        assertThat(result.read().messageIds()).containsExactly("m1");
        assertThat(result.read().userImageUrl()).isEqualTo("/images/profiles/viewer/small.webp");
        verify(chatStreamPublisher).publish(org.mockito.ArgumentMatchers.argThat(event ->
                event != null
                        && "read".equals(event.type())
                        && "chat-1".equals(event.chatId())
                        && "viewer".equals(event.senderId())
                        && event.messageIds().equals(List.of("m1"))));
        verify(chatsRepository).save(org.mockito.ArgumentMatchers.argThat(chat ->
                chat.getUnread() == 0 && chat.getRevision() > viewerChat.getRevision()));
        verify(mongoTemplate).updateMulti(any(Query.class), any(), eq(ChatMessage.class));
    }

    @Test
    void notificationTargetReadsOnlyItsMessageAndKeepsOtherNotificationsUnread() {
        when(usersRepository.findByUserIdIn(anyCollection())).thenReturn(List.of(user("sender", "Sender User", "SU"), user("viewer", "Viewer User", "VU")));
        Chat viewerChat = chat("chat-1", "viewer", 2, "2026-05-26T08:00:00Z", "contact");
        Chat senderChat = chat("chat-1", "sender", 0);
        ChatMessage target = message("target", "sender", "someone-else", "2026-05-26T08:00:02Z");
        ChatMessage previous = message("previous", "sender", "someone-else", "2026-05-26T08:00:01Z");
        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(chatsRepository.findByChatId("chat-1")).thenReturn(List.of(viewerChat, senderChat));
        when(chatMessagesRepository.findByChatIdAndMessageId("chat-1", "target")).thenReturn(Optional.of(target));
        when(chatMessagesRepository.countByChatId("chat-1")).thenReturn(2L);
        when(chatMessagesRepository.findByChatIdOrderBySentAtIsoDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of(target, previous));


        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ChatMessage.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(chatsRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.queryMessagesPage("chat-1", "viewer", 15, null, "target");

        assertThat(result.read().messageIds()).containsExactly("target");
        assertThat(result.read().unread()).isEqualTo(1);
        verify(userNotificationService).markChatMessagesRead("viewer", "chat-1", List.of("target"));
        verify(userNotificationService, never()).markChatRead(anyString(), anyString());
        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(Update.class), eq(ChatMessage.class));
    }

    @Test
    void openingOlderMessagePageAddsReaderWithAtomicMongoUpdate() {
        when(usersRepository.findByUserIdIn(anyCollection())).thenReturn(List.of(user("sender", "Sender User", "SU"), user("viewer", "Viewer User", "VU")));
        Chat viewerChat = chat("chat-1", "viewer", 1, "2026-05-26T08:00:00Z", "contact");
        Chat senderChat = chat("chat-1", "sender", 0);
        ChatMessage unreadMessage = message("m1", "sender", "someone-else", "2026-05-26T08:00:01Z");

        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(chatsRepository.findByChatId("chat-1")).thenReturn(List.of(viewerChat, senderChat));
        when(chatMessagesRepository.countByChatId("chat-1")).thenReturn(16L);
        when(chatMessagesRepository.findByChatIdOrderBySentAtIsoDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of(unreadMessage));


        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ChatMessage.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
        when(chatsRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.queryMessagesPage("chat-1", "viewer", 15, "15");

        assertThat(result.read()).isNotNull();
        assertThat(result.read().messageIds()).containsExactly("m1");
        assertThat(result.items().getFirst().readBy())
                .extracting(reader -> reader.id())
                .contains("viewer", "someone-else");
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(ChatMessage.class));
        verify(chatMessagesRepository, never()).saveAll(anyList());
    }

    @Test
    void reopeningDeletedMessageDoesNotRecreateReadReceipts() {
        when(usersRepository.findByUserIdIn(anyCollection())).thenReturn(List.of(user("sender", "Sender User", "SU")));
        Chat viewerChat = chat("chat-1", "viewer", 0, "2026-05-26T08:00:00Z", "contact");
        Chat senderChat = chat("chat-1", "sender", 0);
        ChatMessage deletedMessage = message("m1", "sender", "someone-else", "2026-05-26T08:00:01Z");
        deletedMessage.setDeletedAtIso("2026-05-26T08:01:00Z");
        deletedMessage.setReadBy(List.of());
        deletedMessage.refreshMessageSearchFields();

        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(chatsRepository.findByChatId("chat-1")).thenReturn(List.of(viewerChat, senderChat));
        when(chatMessagesRepository.countByChatId("chat-1")).thenReturn(1L);
        when(chatMessagesRepository.findByChatIdOrderBySentAtIsoDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of(deletedMessage));


        var result = service.queryMessagesPage("chat-1", "viewer", 10, null);

        assertThat(result.read()).isNull();
        assertThat(result.items().getFirst().readBy()).isEmpty();
        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(Update.class), eq(ChatMessage.class));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(ChatMessage.class));
        verify(chatMessagesRepository, never()).saveAll(anyList());
    }

    @Test
    void queryMembersPageUsesLogicalChatIdAndPaginatesTheMongoUsersQuery() {
        Chat viewerChat = chat("chat-1", "viewer", 0);
        Chat otherOwnerChat = chat("chat-1", "sender", 0);

        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(chatsRepository.findByChatId("chat-1")).thenReturn(List.of(otherOwnerChat, viewerChat));
        when(mongoTemplate.count(any(Query.class), eq(Users.class))).thenReturn(2L);
        when(mongoTemplate.find(any(Query.class), eq(Users.class)))
                .thenReturn(List.of(user("viewer", "Viewer User", "VU")));

        var result = service.queryMembersPage("chat-1", "viewer", 1, null, false);

        assertThat(result.items())
                .extracting(member -> member.id())
                .containsExactly("viewer");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.nextCursor()).isEqualTo("1");
        verify(mongoTemplate).count(any(Query.class), eq(Users.class));
        verify(mongoTemplate).find(any(Query.class), eq(Users.class));
        verify(chatsRepository).findByChatId("chat-1");
        verify(usersRepository, never()).findAll();
    }

    @Test
    void queryChatsReadsOwnerCopiesWithoutRepairingMemberCopies() {
        Chat viewerChat = chat("chat-1", "viewer", 0);

        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        stubEmptyMenuCounters("viewer");
        when(mongoTemplate.count(any(Query.class), eq(Chat.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Chat.class))).thenReturn(List.of(viewerChat));

        assertThat(service.queryChats("viewer")).hasSize(1);
        assertThat(service.queryChats("viewer")).hasSize(1);

        verify(mongoTemplate, times(2)).find(any(Query.class), eq(Chat.class));
        verify(chatsRepository, never()).findByMemberIdsContaining("viewer");
        verify(chatsRepository, never()).findByChatId(anyString());
        verify(chatsRepository, never()).findByOwnerUserIdAndChatId(anyString(), anyString());
        verify(chatsRepository, never()).saveAll(anyList());
    }

    @Test
    void queryChatPageSortsLocalIsoSeedDatesByRequestedDateOrder() {
        Chat oldChat = chat("zz-old", "viewer", 0, "2026-03-01T07:48:00");
        Chat newChat = chat("aa-new", "viewer", 0, "2026-03-01T14:03:00");
        Chat midChat = chat("mm-mid", "viewer", 0, "2026-02-28T17:48:00");

        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        stubEmptyMenuCounters("viewer");
        when(mongoTemplate.count(any(Query.class), eq(Chat.class))).thenReturn(3L);
        when(mongoTemplate.find(any(Query.class), eq(Chat.class))).thenReturn(List.of(newChat, oldChat, midChat));

        var result = service.queryChatPage(
                "viewer",
                "all",
                "date",
                "desc",
                null,
                null,
                10,
                null,
                null,
                false,
                null);

        assertThat(result.items())
                .extracting(item -> item.id())
                .containsExactly("aa-new", "zz-old", "mm-mid");
    }

    @Test
    void sendMessageRejectsDeniedChatBeforeWrite() {
        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(accessControlService.canCurrentActorCreateChatMessage("viewer", "chat-1")).thenReturn(false);

        assertThatThrownBy(() -> service.sendMessage("chat-1", "viewer", "hello"))
                .hasMessageContaining("post in this chat");

        verify(chatMessagesRepository, never()).save(any(ChatMessage.class));
        verify(chatsRepository, never()).findByChatId(anyString());
    }

    @Test
    void adminSupportMessageUsesTheChatMutationSignalsAndOnlyIncrementsTheTargetUnread() {
        String chatId = "c-support-admin-target";
        Users target = user("target", "Target User", "TU");
        Users admin = user("admin", "Admin User", "AU");
        when(adminMenuCounterService.isAdminUser(admin)).thenReturn(true);
        when(usersRepository.findByUserId("target")).thenReturn(Optional.of(target));
        when(usersRepository.findByUserId("admin")).thenReturn(Optional.of(admin));
        when(chatsRepository.findByChatId(chatId)).thenReturn(List.of());
        when(chatsRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatMessagesRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Chat.class))
                .thenReturn(bulkOperations);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Users.class))
                .thenReturn(userCounterBulkOperations);

        var result = service.sendAdminSupportMessage(
                "target",
                "admin",
                "Admin User",
                "AU",
                "Please review this warning.",
                "warned");

        assertThat(result.senderName()).isEqualTo("Admin User");
        assertThat(result.text()).isEqualTo("Please review this warning.");
        ArgumentCaptor<List<Chat>> definitionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatsRepository).saveAll(definitionsCaptor.capture());
        assertThat(definitionsCaptor.getValue()).hasSize(2);
        assertThat(definitionsCaptor.getValue()).allSatisfy(chat -> {
            assertThat(chat.getChannelType()).isEqualTo("appSupport");
            assertThat(chat.getSupportCase().getStatus()).isEqualTo("warned");
        });
        ArgumentCaptor<Update> counterUpdateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(userCounterBulkOperations).updateOne(any(Query.class), counterUpdateCaptor.capture());
        Document increments = counterUpdateCaptor.getValue().getUpdateObject().get("$inc", Document.class);
        assertThat(((Number) increments.get("activities.chats")).intValue()).isEqualTo(1);
        assertThat(((Number) increments.get("activities.chat.all")).intValue()).isEqualTo(1);
        assertThat(((Number) increments.get("activities.chat.appSupport")).intValue()).isEqualTo(1);
        verify(chatPushNotificationService).notifyRecipients(
                org.mockito.ArgumentMatchers.argThat(chats -> chats.stream().anyMatch(chat ->
                        "target".equals(chat.getOwnerUserId()) && Integer.valueOf(1).equals(chat.getUnread()))
                        && chats.stream().anyMatch(chat ->
                        "admin".equals(chat.getOwnerUserId()) && Integer.valueOf(0).equals(chat.getUnread()))),
                org.mockito.ArgumentMatchers.argThat(event ->
                        event != null
                                && "message".equals(event.type())
                                && chatId.equals(event.chatId())
                                && "admin".equals(event.senderId())));
        verify(chatStreamPublisher, timeout(1000)).publish(org.mockito.ArgumentMatchers.argThat(event ->
                event != null
                        && "message".equals(event.type())
                        && chatId.equals(event.chatId())
                        && "admin".equals(event.senderId())));

    }

    @Test
    void editMessageRejectsDeniedChatBeforeLoadingMessage() {
        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(accessControlService.canCurrentActorEditChatMessage("viewer", "chat-1")).thenReturn(false);

        assertThatThrownBy(() -> service.editMessage("chat-1", "viewer", "m1", "updated"))
                .hasMessageContaining("change messages");

        verify(chatMessagesRepository, never()).findByChatIdAndMessageId(anyString(), anyString());
        verify(chatMessagesRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void updateSupportCaseRejectsDeniedChatBeforeSavingState() {
        when(selectedProfile.resolveSelectedUserId("admin")).thenReturn("admin");
        when(accessControlService.can(AccessPolicyKeys.CHAT_SUPPORT_MANAGE, "admin", "admin", "chat-support")).thenReturn(false);

        assertThatThrownBy(() -> service.updateSupportCase("chat-support", "admin", "pick"))
                .hasMessageContaining("support chat");

        verify(chatsRepository, never()).findByChatId(anyString());
        verify(chatsRepository, never()).saveAll(anyList());
    }

    @Test
    void directChatRequiresAnApprovedPairBeforeWriting() {
        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        when(usersRepository.findByUserId("viewer")).thenReturn(Optional.of(user("viewer", "Viewer", "V")));
        assertThatThrownBy(() -> service.ensureContactChat("viewer", "sender"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403");
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(Chat.class));
        verify(usersRepository, never()).findByUserIdIn(anyCollection());
    }

    @Test
    void approvedPairDoesNotBypassTheStoredWorkspaceBoundary() {
        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        Users viewer = user("viewer", "Viewer", "V");
        Users target = user("sender", "Sender", "S"); target.setWorkspaceGroupId("another-workspace");
        when(usersRepository.findByUserId("viewer")).thenReturn(Optional.of(viewer));
        when(usersRepository.findByUserIdIn(anyCollection())).thenReturn(List.of(viewer, target));
        when(mongoTemplate.findById(ContactChatAccess.pairId("viewer", "sender"), ContactChatAccess.class))
                .thenReturn(new ContactChatAccess(ContactChatAccess.pairId("viewer", "sender"), "sender", "viewer",
                        "viewer", "approved", "2026-10-11T00:00:00Z", "2026-10-11T00:00:00Z", 1L));
        assertThatThrownBy(() -> service.ensureContactChat("viewer", "sender"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(Chat.class));
    }

    @Test
    void approvedPersonalChatReopensWithoutReplacingMessagesOrUnreadState() {
        when(selectedProfile.resolveSelectedUserId("viewer")).thenReturn("viewer");
        Users viewer = user("viewer", "Viewer", "V"); Users sender = user("sender", "Sender", "S");
        when(usersRepository.findByUserId("viewer")).thenReturn(Optional.of(viewer));
        when(usersRepository.findByUserIdIn(anyCollection())).thenReturn(List.of(viewer, sender));
        when(mongoTemplate.findById(ContactChatAccess.pairId("viewer", "sender"), ContactChatAccess.class))
                .thenReturn(new ContactChatAccess(ContactChatAccess.pairId("viewer", "sender"), "sender", "viewer",
                        "viewer", "approved", "2026-10-11T00:00:00Z", "2026-10-11T00:00:00Z", 1L));
        String chatId = "c-contact-" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("sender\nviewer".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Chat existing = chat(chatId, "viewer", 7, "2026-10-11T00:00:00Z", "contact");
        when(chatsRepository.findByChatId(chatId)).thenReturn(List.of(existing));
        assertThat(service.ensureContactChat("viewer", "sender").unread()).isEqualTo(7);
        var updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).upsert(any(Query.class), updates.capture(), eq(Chat.class));
        assertThat(updates.getAllValues()).allSatisfy(update ->
                assertThat(update.getUpdateObject().keySet()).containsExactly("$setOnInsert"));
        verify(usersRepository, times(2)).findByUserIdIn(anyCollection());
        verify(usersRepository).findByUserId("viewer");
        verify(mongoTemplate).findById(ContactChatAccess.pairId("viewer", "sender"), ContactChatAccess.class);
        org.mockito.Mockito.verifyNoInteractions(chatMessagesRepository);
    }

    @Test
    void anUnrelatedMemberCannotReadTheConversation() {
        when(selectedProfile.resolveSelectedUserId("outsider")).thenReturn("outsider");
        when(chatsRepository.findByChatId("chat-1")).thenReturn(List.of(chat("chat-1", "viewer", 0)));
        assertThatThrownBy(() -> service.queryMessagesPage("chat-1", "outsider", 20, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404");
        org.mockito.Mockito.verifyNoInteractions(chatMessagesRepository);
    }

    @Test
    void aMemberCannotCreateAnAdministratorSupportMessage() {
        when(usersRepository.findByUserId("target")).thenReturn(Optional.of(user("target", "Target", "T")));
        when(usersRepository.findByUserId("member")).thenReturn(Optional.of(user("member", "Member", "M")));
        assertThatThrownBy(() -> service.sendAdminSupportMessage("target", "member", "Member", "M", "warning", "warned"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403");
        org.mockito.Mockito.verifyNoInteractions(chatsRepository, chatMessagesRepository);
    }

    private Chat chat(String chatId, String ownerUserId, int unread) {
        return chat(chatId, ownerUserId, unread, "2026-05-26T08:00:00Z");
    }

    private void stubEmptyMenuCounters(String userId) {
        when(homeUserProfileService.queryMenuCounters(userId)).thenReturn(new UserMenuCountersDto(0, new UserChatCountersDto(0, 0, null, 0), 0));
    }

    private Chat chat(String chatId, String ownerUserId, int unread, String dateIso) {
        return chat(chatId, ownerUserId, unread, dateIso, null);
    }

    private Chat chat(String chatId, String ownerUserId, int unread, String dateIso, String channelType) {
        Chat chat = new Chat();
        chat.setMongoId("chat-owner-copy:" + ownerUserId + ":" + chatId);
        chat.setChatId(chatId);
        chat.setOwnerUserId(ownerUserId);
        chat.setTitle("Test chat");
        chat.setLastMessage("hello");
        chat.setDateIso(dateIso);
        chat.setMemberIds(List.of("viewer", "sender"));
        chat.setUnread(unread);
        chat.setChannelType(channelType);
        return chat;
    }

    private ChatMessage message(String messageId, String senderUserId, String readerUserId, String sentAtIso) {
        ChatMessage message = new ChatMessage();
        message.setMongoId("chat-message:chat-1:" + messageId);
        message.setChatId("chat-1");
        message.setId(messageId);
        message.setSender("Sender User");
        message.setSenderAvatar(avatar(senderUserId, "SU"));
        message.setText("hello");
        message.setSentAtIso(sentAtIso);
        message.setReadBy(List.of(readAvatar(readerUserId, "VU")));
        message.refreshMessageSearchFields();
        return message;
    }

    private ChatAvatarRecord avatar(String userId, String initials) {
        ChatAvatarRecord avatar = new ChatAvatarRecord();
        avatar.setId(userId);
        avatar.setInitials(initials);
        avatar.setGender("man");
        return avatar;
    }

    private ChatReadAvatarRecord readAvatar(String userId, String initials) {
        ChatReadAvatarRecord reader = new ChatReadAvatarRecord();
        reader.setId(userId);
        reader.setInitials(initials);
        reader.setGender("man");
        return reader;
    }

    private Users user(String userId, String name, String initials) {
        Users user = new Users();
        user.setId(userId);
        user.setName(name);
        user.setInitials(initials);
        user.setGender("man");
        user.setStatus("A");
        user.setProfileStatus("public");
        return user;
    }

    private com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService profileDirectory() {
        var owner = new com.raxim.myscoutee.domain.accounts.service.ProfileDirectoryService(usersRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(owner, "mongo", mongoTemplate);
        return owner;
    }

}
