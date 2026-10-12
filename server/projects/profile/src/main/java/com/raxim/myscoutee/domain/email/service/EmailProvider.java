package com.raxim.myscoutee.domain.email.service;

import com.raxim.myscoutee.domain.email.api.EmailSendResult;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;

public interface EmailProvider {
    default com.raxim.myscoutee.domain.email.service.OperatorEmailConfigurationService.Provider configurationDescriptor() { return null; }
    default EmailSendResult sendConfigured(EmailSendRequest request, String credential) { throw new UnsupportedOperationException(); }

    String providerKey();

    EmailSendResult send(EmailSendRequest request);
}
