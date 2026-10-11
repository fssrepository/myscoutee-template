package com.raxim.myscoutee.domain.integrations.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared bounds and absence handling for the existing integration read routes. */
final class IntegrationReadSupport {
    private IntegrationReadSupport() {}
    static void pageLimit(int limit) {
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100.");
    }
    static <T> T found(T value) {
        if (value == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return value;
    }
}
