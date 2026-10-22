package com.raxim.myscoutee.microservices.transport.chat;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.chat.api.ChatQueries;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class ChatQueriesEndpoint {
    private final ChatQueries owner;
    private final ObjectMapper mapper;
    private final com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Verifier verifier;
    public ChatQueriesEndpoint(ChatQueries owner, ObjectMapper mapper, com.raxim.myscoutee.microservices.transport.security.ActorDelegation.Verifier verifier) { this.owner = owner; this.mapper = mapper; this.verifier = verifier; }
    @PostMapping(value = ChatQueriesProtocol.QUERYCHATBYID_1, produces = "application/json")
    public ResponseEntity<?> querychatbyid_1(HttpServletRequest request) throws java.io.IOException {
        return read(request, ChatQueriesProtocol.QueryChatById1.class, ChatQueriesProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new ChatQueriesProtocol.QueryChatById1Result(true, owner.queryChatById(command.chatId(), command.requestedUserId()))));
    }
    @PostMapping(value = ChatQueriesProtocol.QUERYCHATPAGE_1, produces = "application/json")
    public ResponseEntity<?> querychatpage_1(HttpServletRequest request) throws java.io.IOException {
        return read(request, ChatQueriesProtocol.QueryChatPage1.class, ChatQueriesProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new ChatQueriesProtocol.QueryChatPage1Result(true, owner.queryChatPage(command.requestedUserId(), command.contextFilterValue(), command.sortValue(), command.sortDirectionValue(), command.secondaryFilterValue(), command.cursor(), command.limitValue(), command.rangeStartIso(), command.rangeEndIso(), command.adminServiceOnly(), command.supportCaseFilterValue()))));
    }
    @PostMapping(value = ChatQueriesProtocol.QUERYMESSAGESPAGE_1, produces = "application/json")
    public ResponseEntity<?> querymessagespage_1(HttpServletRequest request) throws java.io.IOException {
        return read(request, ChatQueriesProtocol.QueryMessagesPage1.class, ChatQueriesProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new ChatQueriesProtocol.QueryMessagesPage1Result(true, owner.queryMessagesPage(command.chatId(), command.requestedUserId(), command.limit(), command.cursor()))));
    }
    @PostMapping(value = ChatQueriesProtocol.QUERYMESSAGESPAGE_2, produces = "application/json")
    public ResponseEntity<?> querymessagespage_2(HttpServletRequest request) throws java.io.IOException {
        return read(request, ChatQueriesProtocol.QueryMessagesPage2.class, ChatQueriesProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new ChatQueriesProtocol.QueryMessagesPage2Result(true, owner.queryMessagesPage(command.chatId(), command.requestedUserId(), command.limit(), command.cursor(), command.targetMessageId()))));
    }
    @PostMapping(value = ChatQueriesProtocol.QUERYMESSAGESPAGE_3, produces = "application/json")
    public ResponseEntity<?> querymessagespage_3(HttpServletRequest request) throws java.io.IOException {
        return read(request, ChatQueriesProtocol.QueryMessagesPage3.class, ChatQueriesProtocol::valid, command ->
                com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new ChatQueriesProtocol.QueryMessagesPage3Result(true, owner.queryMessagesPage(command.chatId(), command.requestedUserId(), command.limit(), command.cursor(), command.targetMessageId(), command.markViewed()))));
    }
    private ChatQueriesProtocol.Receipt receipt(Runnable action) { action.run(); return new ChatQueriesProtocol.Receipt(true); }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            if (ChatQueriesProtocol.delegated(request.getRequestURI())) {
                var verified = com.raxim.myscoutee.microservices.transport.security.DelegatedRequestBodies.read(
                        request, mapper, verifier, ChatQueriesProtocol.AUDIENCE, request.getRequestURI(), type, ChatQueriesProtocol.MAX_REQUEST_BYTES);
                if (!valid.test(verified.value())) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
                return verified.call(() -> response(200, action.apply(verified.value())));
            }
            byte[] bytes = ReadQueryBodies.readBytes(request, ChatQueriesProtocol.MAX_REQUEST_BYTES);
            T query = ReadQueryBodies.decode(bytes, mapper, type, valid, true);
            return response(200, action.apply(query));
        } catch (org.springframework.security.access.AccessDeniedException denied) { return response(403, new ChatQueriesProtocol.Error("INVALID_DELEGATION")); }
        catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new ChatQueriesProtocol.Error(invalid.code())); }
        catch (org.springframework.web.server.ResponseStatusException rejected) {
            return response(rejected.getStatusCode().value(), new ChatQueriesProtocol.Error("OWNER_REJECTED"));
        }
        catch (RuntimeException unavailable) { return response(503, new ChatQueriesProtocol.Error("CHAT_QUERIES_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
