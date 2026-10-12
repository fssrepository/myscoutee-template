package com.raxim.myscoutee.microservices.transport.accounts;

import java.util.function.Function;
import java.util.function.Predicate;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController
public final class AccountNotificationsEndpoint {
    private final AccountNotifications owner;
    private final ObjectMapper mapper;
    public AccountNotificationsEndpoint(AccountNotifications owner, ObjectMapper mapper) { this.owner = owner; this.mapper = mapper; }
    @PostMapping(value = AccountNotificationsProtocol.RECIPIENTS, produces = "application/json")
    public ResponseEntity<?> recipients(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountNotificationsProtocol.Ids.class, AccountNotificationsProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountNotificationsProtocol.Recipients(owner.recipients(command.ids()))));
    }
    @PostMapping(value = AccountNotificationsProtocol.SENDERS, produces = "application/json")
    public ResponseEntity<?> senders(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountNotificationsProtocol.Ids.class, AccountNotificationsProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountNotificationsProtocol.Senders(owner.senders(command.ids()))));
    }
    @PostMapping(value = AccountNotificationsProtocol.ROLE_RECIPIENTS, produces = "application/json")
    public ResponseEntity<?> roleRecipients(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountNotificationsProtocol.Ids.class, AccountNotificationsProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountNotificationsProtocol.Roles(owner.roleRecipients(command.ids()))));
    }
    @PostMapping(value = AccountNotificationsProtocol.NOTIFICATION_ACCOUNT_ID, produces = "application/json")
    public ResponseEntity<?> notificationAccountId(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountNotificationsProtocol.Id.class, AccountNotificationsProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountNotificationsProtocol.Text(true, owner.notificationAccountId(command.id()))));
    }
    @PostMapping(value = AccountNotificationsProtocol.MUTED, produces = "application/json")
    public ResponseEntity<?> muted(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountNotificationsProtocol.Id.class, AccountNotificationsProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountNotificationsProtocol.Decision(owner.muted(command.id()))));
    }
    @PostMapping(value = AccountNotificationsProtocol.SET_MUTED, produces = "application/json")
    public ResponseEntity<?> setMuted(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountNotificationsProtocol.Mute.class, AccountNotificationsProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> receipt(() -> owner.setMuted(command.accountId(), command.muted()))));
    }
    @PostMapping(value = AccountNotificationsProtocol.INCREMENT_UNREAD_COUNTER, produces = "application/json")
    public ResponseEntity<?> incrementUnreadCounter(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountNotificationsProtocol.Increment.class, AccountNotificationsProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> receipt(() -> owner.incrementUnreadCounter(command.accountId(), command.delta()))));
    }
    @PostMapping(value = AccountNotificationsProtocol.REMOVE_INVALID_PUSH_TOKENS, produces = "application/json")
    public ResponseEntity<?> removeInvalidPushTokens(HttpServletRequest request) throws java.io.IOException {
        return read(request, AccountNotificationsProtocol.Cleanup.class, AccountNotificationsProtocol::valid,
                command -> com.raxim.myscoutee.domain.platform.api.JobGroupContext.call(command.scope().active(), command.scope().groupId(), () -> new AccountNotificationsProtocol.CleanupResult(owner.removeInvalidPushTokens(command.accountIds(), command.invalidTokens()))));
    }
    private AccountNotificationsProtocol.Receipt receipt(Runnable action) { action.run(); return new AccountNotificationsProtocol.Receipt(true); }
    private <T> ResponseEntity<?> read(HttpServletRequest request, Class<T> type, Predicate<T> valid, Function<T, Object> action)
            throws java.io.IOException {
        try {
            byte[] bytes = ReadQueryBodies.readBytes(request, AccountNotificationsProtocol.MAX_REQUEST_BYTES);
            T query;
            try {
                query = mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(bytes);
            } catch (RuntimeException invalid) { throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST"); }
            if (!valid.test(query)) throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
            return response(200, action.apply(query));
        } catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new AccountNotificationsProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new AccountNotificationsProtocol.Error("ACCOUNT_NOTIFICATIONS_OUTCOME_UNKNOWN")); }
    }
    private ResponseEntity<?> response(int status, Object body) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body); }
}
