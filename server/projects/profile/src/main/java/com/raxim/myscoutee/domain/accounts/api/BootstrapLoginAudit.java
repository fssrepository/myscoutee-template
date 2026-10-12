package com.raxim.myscoutee.domain.accounts.api;

public interface BootstrapLoginAudit {
    void recordBootstrapLogin(String verifiedEmail, jakarta.servlet.http.HttpServletRequest request);
}
