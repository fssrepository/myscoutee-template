package com.raxim.myscoutee.microservices.transport.platform;

import com.raxim.myscoutee.domain.platform.api.OperatorNotificationSettings;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public final class OperatorNotificationSettingsEndpoint {
    private final OperatorNotificationSettings owner;
    public OperatorNotificationSettingsEndpoint(OperatorNotificationSettings owner) { this.owner = owner; }
    @GetMapping(value = OperatorNotificationSettingsProtocol.PATH, produces = "application/json")
    public ResponseEntity<?> read() {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(java.util.Objects.requireNonNull(owner.operatorNotificationRecipients()));
        } catch (RuntimeException unavailable) {
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(new OperatorNotificationSettingsProtocol.Error("OPERATOR_NOTIFICATION_SETTINGS_UNAVAILABLE"));
        }
    }
}
