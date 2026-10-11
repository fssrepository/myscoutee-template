package com.raxim.myscoutee.domain.accounts.api;
public interface BootstrapAccounts {
    org.springframework.security.core.userdetails.UserDetails loadOrCreateBootstrapOperator(String email);
    org.springframework.security.core.userdetails.UserDetails loadStoredBootstrapOperator(String email);
}
