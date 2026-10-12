package com.raxim.myscoutee.domain.platform.api;

/** Live deployment authentication flag. Does not select a request's REAL/DEMO database. */
public interface AuthenticationMode {
    boolean isFirebaseEnabled();
}
