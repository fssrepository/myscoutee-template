package com.raxim.myscoutee.domain.accounts.security.firebase.dto;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.raxim.myscoutee.domain.accounts.document.Users;

public class FirebasePrincipal implements UserDetails, com.raxim.myscoutee.domain.accounts.api.AccountIdentity {
    private final String userId;
    private final String email;
    private final List<String> authorities;
    private final Users user;
    private final boolean emailVerified;

    public FirebasePrincipal(String userId, String email, String role) {
        this(userId, email, role, null, false);
    }

    public FirebasePrincipal(String userId, String email, String role, Users user) {
        this(userId, email, role, user, false);
    }

    public FirebasePrincipal(
            String userId,
            String email,
            String role,
            Users user,
            boolean emailVerified) {
        this(
                userId,
                email,
                role == null ? List.of() : List.of(role),
                user,
                emailVerified);
    }

    public FirebasePrincipal(
            String userId,
            String email,
            Collection<String> authorities,
            Users user) {
        this(userId, email, authorities, user, false);
    }

    public FirebasePrincipal(
            String userId,
            String email,
            Collection<String> authorities,
            Users user,
            boolean emailVerified) {
        this.userId = userId;
        this.email = email;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (authorities != null) {
            authorities.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(authority -> !authority.isEmpty())
                    .forEach(normalized::add);
        }
        this.authorities = List.copyOf(normalized);
        this.user = user;
        this.emailVerified = emailVerified;
    }

    @Override
    public Collection<SimpleGrantedAuthority> getAuthorities() {
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    public String getUserId() {
        if (user != null && user.getId() != null && !user.getId().isBlank()) {
            return user.getId();
        }
        return userId;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Returns the verification state asserted by the decoded Firebase token.
     * Legacy, bootstrap, and non-token principals deliberately default to
     * {@code false}.
     */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    public com.raxim.myscoutee.domain.accounts.api.WorkspaceProfile profileView() { return user; }

    public Users getUser() {
        return user;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        if (email != null && !email.isBlank()) {
            return email;
        }
        return getUserId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
