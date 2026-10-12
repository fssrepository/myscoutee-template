package com.raxim.myscoutee.domain.accounts.security.firebase;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.util.StringUtils;

public final class DevelopmentIdentityEmail {
    private static final String DOMAIN = "@myscoutee.local";

    private DevelopmentIdentityEmail() {
    }

    public static String forProfile(String profileId) {
        String normalizedProfileId = profileId == null ? "" : profileId.trim();
        if (!StringUtils.hasText(normalizedProfileId)) {
            throw new IllegalArgumentException("A profile id is required for a development identity email.");
        }
        String stableSuffix = UUID.nameUUIDFromBytes(
                        normalizedProfileId.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return "member+" + stableSuffix + DOMAIN;
    }
}
