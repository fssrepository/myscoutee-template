package com.raxim.myscoutee.microservices.transport.help;

import java.util.*;
import com.raxim.myscoutee.microservices.transport.common.ProtocolFields;

public final class HelpPrivacyProtocol {
    public static final String ANONYMIZE_AUTHORS = "/_internal/v1/help/help-privacy/anonymize-authors";
    public static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024, MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private HelpPrivacyProtocol() {}
    public static String[] paths() { return new String[]{ANONYMIZE_AUTHORS}; }
    private static boolean id(String value) { return ProtocolFields.requiredId(value); }
    private static boolean scope(ProtocolFields.JobScope value) {
        return value != null && ProtocolFields.jobScope(value.active(), value.groupId());
    }
    public static boolean valid(ProtocolFields.UserCommand value) { return ProtocolFields.validUserCommand(value); }
}
