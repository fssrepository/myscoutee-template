package com.raxim.myscoutee.microservices.transport.platform;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import com.raxim.myscoutee.domain.platform.api.AdministratorSettings;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class RemoteAdministratorSettings extends RemoteReadTransport implements AdministratorSettings {
    private final ObjectMapper mapper;
    public RemoteAdministratorSettings(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics); this.mapper = mapper;
    }
    @Override public List<String> adminEmails() {
        return exchange(AdministratorSettingsProtocol.EMAILS, new TypeReference<List<String>>() {}, "platform.admin-emails",
                1024 * 1024, values -> values.stream().allMatch(Objects::nonNull));
    }
    @Override public boolean isAdminEmail(String email) {
        return email != null && adminEmailsAmong(Collections.singletonList(email)).contains(email);
    }
    @Override public Set<String> adminEmailsAmong(Collection<String> emails) {
        if (!AdministratorSettingsProtocol.valid(emails)) throw new IllegalArgumentException("Administrator email query exceeds limits");
        if (emails.isEmpty()) return Set.of();
        byte[] body = mapper.writeValueAsBytes(new AdministratorSettingsProtocol.Emails(emails));
        if (body.length > AdministratorSettingsProtocol.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Administrator email query exceeds limits");
        Set<String> requested = new HashSet<>(emails);
        return exchange(AdministratorSettingsProtocol.CLASSIFY, body, new TypeReference<Set<String>>() {},
                "platform.admin-classification", 1024 * 1024, values -> values.stream().allMatch(value -> value != null && requested.contains(value)));
    }
}
