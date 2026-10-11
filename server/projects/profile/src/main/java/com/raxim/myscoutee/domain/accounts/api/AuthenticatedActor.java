package com.raxim.myscoutee.domain.accounts.api;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import com.raxim.myscoutee.domain.accounts.dto.ProfileIdentityDto;

/**
 * Identity exposed by an authenticated principal, without an account document.
 * Implementations must be installed by the authentication boundary; request
 * parameters and service credentials alone are not authenticated actors.
 */
public interface AuthenticatedActor {
    String getUserId();
    String getEmail();
    Collection<? extends GrantedAuthority> getAuthorities();
    ProfileIdentityDto getProfileIdentity();
}
