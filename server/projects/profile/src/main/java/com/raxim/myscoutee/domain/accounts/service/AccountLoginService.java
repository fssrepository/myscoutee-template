package com.raxim.myscoutee.domain.accounts.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.accounts.api.AccountCredentialMarker;
import com.raxim.myscoutee.domain.accounts.api.SessionRequestSupport;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserSessionLoginRequestDto;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseAuthenticationToken;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseService;
import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseTokenVerifier;
import com.raxim.myscoutee.domain.accounts.security.firebase.exception.FirebaseTokenInvalidException;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import com.raxim.myscoutee.domain.platform.security.bootstrap.BootstrapAccountAuthenticationService;
import com.raxim.myscoutee.domain.platform.security.bootstrap.PasswordLoginTransportSecurity;

import jakarta.servlet.http.HttpServletRequest;

/** Login proofs end here. All accepted accounts enter the same application session flow. */
@Service
public class AccountLoginService {
    private final BootstrapAccountAuthenticationService passwords;
    private final FirebaseTokenVerifier identityTokens;
    private final FirebaseService accounts;
    private final RuntimeConfiguration config;

    public AccountLoginService(BootstrapAccountAuthenticationService passwords, FirebaseTokenVerifier identityTokens,
            FirebaseService accounts, RuntimeConfiguration config) {
        this.passwords = passwords;
        this.identityTokens = identityTokens;
        this.accounts = accounts;
        this.config = config;
    }

    public void authenticate(UserSessionLoginRequestDto login, HttpServletRequest request) {
        if (MongoSessionModeContext.currentMode() == MongoSessionMode.DEMO) {
            if (login.email() != null || login.password() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password login requires the real account store.");
            }
            var selected = SecurityContextHolder.getContext().getAuthentication();
            if (selected == null || !selected.isAuthenticated()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A selected demo account is required.");
            }
            return;
        }
        if (login.email() != null || login.password() != null) {
            if (!PasswordLoginTransportSecurity.isAllowed(request, config)) {
                throw new ResponseStatusException(HttpStatus.UPGRADE_REQUIRED, "HTTPS is required for password login.");
            }
            var account = passwords.authenticate(login.email(), login.password());
            SecurityContextHolder.getContext().setAuthentication(
                    new FirebaseAuthenticationToken(account, new PasswordCredential(), account.getAuthorities()));
            return;
        }
        String token = SessionRequestSupport.bearerToken(request);
        if (!config.isFirebaseEnabled() || token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A verified login credential is required.");
        }
        try {
            var credential = identityTokens.verify(token);
            var account = accounts.loadUserByCredential(credential, request.getHeader("X-Authorization-Link"), request.getRequestURI());
            SecurityContextHolder.getContext().setAuthentication(
                    new FirebaseAuthenticationToken(account, credential, account.getAuthorities()));
        } catch (FirebaseTokenInvalidException invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login credential.");
        }
    }

    public record PasswordCredential() implements AccountCredentialMarker {}
}
