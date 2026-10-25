package com.raxim.myscoutee.domain.accounts.api;
import org.springframework.security.core.Authentication;
public interface ProfileAuthentication {
 AccountIdentity integrationPrincipal(WorkspaceProfile profile);
 boolean hasAccountCredentials(Authentication authentication);
 boolean isAccount(Authentication authentication);
 Authentication forProfile(Authentication original, WorkspaceProfile profile);
    org.springframework.security.core.Authentication trustedPrincipal(org.springframework.security.core.userdetails.UserDetails principal);
}
