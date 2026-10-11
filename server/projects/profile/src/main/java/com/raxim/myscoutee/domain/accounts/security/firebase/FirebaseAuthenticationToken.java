package com.raxim.myscoutee.domain.accounts.security.firebase;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class FirebaseAuthenticationToken extends AbstractAuthenticationToken {
    private static final long serialVersionUID = -1869548136546750302L;
    
    private final Object principal;
    private Object credentials;

    public FirebaseAuthenticationToken(Object principal, Object credentials) {
        super((Collection<? extends GrantedAuthority>) null);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    public FirebaseAuthenticationToken(Object principal, Object credentials,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    /** Rebind the authenticated account to a profile already authorized by the caller. */
    public static FirebaseAuthenticationToken forProfile(org.springframework.security.core.Authentication original,
            com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile profile) {
        if (!(original.getPrincipal() instanceof com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal account))
            throw new org.springframework.security.authentication.InsufficientAuthenticationException("Account required");
        var principal = new com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal(profile.getUserId(), account.getEmail(),
                original.getAuthorities().stream().map(a -> a.getAuthority()).toList(), (com.raxim.myscoutee.domain.accounts.document.Users) profile, account.isEmailVerified());
        return new FirebaseAuthenticationToken(principal, original.getCredentials(), principal.getAuthorities());
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {
        super.setAuthenticated(isAuthenticated);
        /*throw new IllegalArgumentException(
                "Cannot set this token to trusted - use constructor which takes a GrantedAuthority list instead");*/
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        credentials = null;
    }
}
