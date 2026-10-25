package com.raxim.myscoutee.domain.accounts.service;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import com.raxim.myscoutee.domain.accounts.api.*;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseAuthenticationToken;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
@Service
public class ProfileAuthenticationService implements ProfileAuthentication {
 public AccountIdentity integrationPrincipal(WorkspaceProfile profile) { return new FirebasePrincipal(profile.getUserId(),null,"INTEGRATION",(Users)profile); }
 public boolean hasAccountCredentials(Authentication authentication) { return AccountCredentialMarker.isPresent(authentication); }
 public boolean isAccount(Authentication authentication) { return authentication!=null && authentication.getPrincipal() instanceof FirebasePrincipal; }
 public Authentication forProfile(Authentication original, WorkspaceProfile profile) { return FirebaseAuthenticationToken.forProfile(original,profile); }
    public org.springframework.security.core.Authentication trustedPrincipal(org.springframework.security.core.userdetails.UserDetails principal) {
        return new com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
