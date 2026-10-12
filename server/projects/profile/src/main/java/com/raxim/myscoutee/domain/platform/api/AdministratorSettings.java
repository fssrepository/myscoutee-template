package com.raxim.myscoutee.domain.platform.api;

import java.util.List;

public interface AdministratorSettings {
    boolean isAdminEmail(String email);
    List<String> adminEmails();
    /** Batch boundary; the owner still applies its existing enabled/normalization policy. */
    default java.util.Set<String> adminEmailsAmong(java.util.Collection<String> emails) {
        return emails.stream().filter(this::isAdminEmail)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
