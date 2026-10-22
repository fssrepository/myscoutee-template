package com.raxim.myscoutee.microservices.transport.accounts;

import com.raxim.myscoutee.domain.accounts.api.AccountDevices;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.microservices.transport.common.ReadQueryBodies;
import com.raxim.myscoutee.microservices.transport.security.ActorDelegation;
import com.raxim.myscoutee.microservices.transport.security.DelegatedRequestBodies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
public final class AccountDevicesEndpoint {
    private final AccountDevices owner;
    private final SelectedProfile profiles;
    private final ActorDelegation.Verifier verifier;
    private final ObjectMapper mapper;
    public AccountDevicesEndpoint(AccountDevices owner, SelectedProfile profiles, ActorDelegation.Verifier verifier, ObjectMapper mapper) {
        this.owner = owner; this.profiles = profiles; this.verifier = verifier; this.mapper = mapper;
    }
    @PostMapping(value = AccountDevicesProtocol.REGISTER, produces = "application/json")
    public ResponseEntity<?> register(HttpServletRequest request) throws java.io.IOException {
        return execute(request, AccountDevicesProtocol.REGISTER, AccountDevicesProtocol.Register.class);
    }
    @PostMapping(value = AccountDevicesProtocol.REMOVE, produces = "application/json")
    public ResponseEntity<?> remove(HttpServletRequest request) throws java.io.IOException {
        return execute(request, AccountDevicesProtocol.REMOVE, AccountDevicesProtocol.Remove.class);
    }
    private <T> ResponseEntity<?> execute(HttpServletRequest request, String path, Class<T> type) throws java.io.IOException {
        try {
            var verified = DelegatedRequestBodies.read(request, mapper, verifier, AccountDevicesProtocol.AUDIENCE,
                    path, type, AccountDevicesProtocol.MAX_REQUEST_BYTES);
            return verified.call(() -> {
                String userId;
                if (verified.value() instanceof AccountDevicesProtocol.Register command) {
                    if (!AccountDevicesProtocol.valid(command.userId(), command.deviceId(), command.firebaseToken())
                            || !AccountDevicesProtocol.bounded(command.platform(), 512))
                        throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
                    userId = command.userId();
                } else if (verified.value() instanceof AccountDevicesProtocol.Remove command) {
                    if (!AccountDevicesProtocol.valid(command.userId(), command.deviceId(), command.firebaseToken()))
                        throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
                    userId = command.userId();
                } else throw new ReadQueryBodies.RejectedQuery(400, "INVALID_REQUEST");
                var selected = profiles.resolveSelectedIdentity(userId);
                if (selected == null || !selected.equals(verified.principal().getProfileIdentity())
                        || !userId.equals(selected.userId())) throw new AccessDeniedException("Actor mismatch");
                if (verified.value() instanceof AccountDevicesProtocol.Register command)
                    owner.register(userId, command.deviceId(), command.firebaseToken(), command.platform(), command.enabled());
                else {
                    var command = (AccountDevicesProtocol.Remove) verified.value();
                    owner.remove(userId, command.deviceId(), command.firebaseToken());
                }
                return response(200, new AccountDevicesProtocol.Receipt(true));
            });
        } catch (AccessDeniedException denied) { return response(403, new AccountDevicesProtocol.Error("INVALID_DELEGATION")); }
        catch (ReadQueryBodies.RejectedQuery invalid) { return response(invalid.status(), new AccountDevicesProtocol.Error(invalid.code())); }
        catch (RuntimeException unavailable) { return response(503, new AccountDevicesProtocol.Error("DEVICE_UPDATE_UNAVAILABLE")); }
    }
    private ResponseEntity<?> response(int status, Object value) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(value); }
}
