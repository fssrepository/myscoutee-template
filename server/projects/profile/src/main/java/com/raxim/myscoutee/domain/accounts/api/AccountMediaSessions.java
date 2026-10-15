package com.raxim.myscoutee.domain.accounts.api;

import java.util.Optional;

public interface AccountMediaSessions {
    String SESSION_ID_HEADER = "X-App-Session-Id";
    Optional<String> authenticateMediaToken(String userId, String tokenHash);
    Optional<MediaToken> mediaToken(String userId, String sessionId);
    boolean replaceMediaToken(String userId, String sessionId, String tokenHash);
    record MediaToken(String hash) {}
}
