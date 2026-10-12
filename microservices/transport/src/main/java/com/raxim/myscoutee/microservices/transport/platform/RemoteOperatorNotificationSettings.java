package com.raxim.myscoutee.microservices.transport.platform;

import java.net.URI;
import java.time.Duration;
import com.raxim.myscoutee.domain.platform.api.OperatorNotificationSettings;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reads current owner state; a transport failure is never replaced with a deployment default. */
public final class RemoteOperatorNotificationSettings extends RemoteReadTransport implements OperatorNotificationSettings {
    public RemoteOperatorNotificationSettings(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
    @Override public com.raxim.myscoutee.domain.platform.dto.OperatorNotificationRecipientsDto operatorNotificationRecipients() {
        return exchange(OperatorNotificationSettingsProtocol.PATH, new TypeReference<com.raxim.myscoutee.domain.platform.dto.OperatorNotificationRecipientsDto>() {},
                "operator-notification-settings.read", OperatorNotificationSettingsProtocol.MAX_RESPONSE_BYTES, value -> value.users() != null);
    }
}
