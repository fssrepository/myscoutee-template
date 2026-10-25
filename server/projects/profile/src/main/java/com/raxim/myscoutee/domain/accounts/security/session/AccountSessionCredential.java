package com.raxim.myscoutee.domain.accounts.security.session;

/** Verified account session; authorities are loaded from the current account record. */
public record AccountSessionCredential(String userId, String sessionId, boolean emailVerified)
        implements com.raxim.myscoutee.domain.accounts.api.AccountCredentialMarker {}
