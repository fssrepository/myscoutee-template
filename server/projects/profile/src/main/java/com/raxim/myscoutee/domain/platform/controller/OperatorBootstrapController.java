package com.raxim.myscoutee.domain.platform.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapAuthenticationService;
import com.raxim.myscoutee.domain.platform.api.OperatorBootstrapRequestSupport;
import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapTransportSecurity;
import com.raxim.myscoutee.common.config.mongo.DemoSessionRequestSupport;
import com.raxim.myscoutee.domain.platform.api.RuntimeConfiguration;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.OperatorBootstrapLoginRequestDto;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.OperatorBootstrapLoginResponseDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(OperatorBootstrapRequestSupport.LOGIN_PATH)
public class OperatorBootstrapController {
    private final OperatorBootstrapAuthenticationService service;
    private final RuntimeConfiguration config;
    private com.raxim.myscoutee.domain.accounts.api.BootstrapLoginAudit loginSessions;

    @org.springframework.beans.factory.annotation.Autowired
    void setLoginSessions(com.raxim.myscoutee.domain.accounts.api.BootstrapLoginAudit value) {
        this.loginSessions = value;
    }

    public OperatorBootstrapController(
            OperatorBootstrapAuthenticationService service,
            RuntimeConfiguration config) {
        this.service = service;
        this.config = config;
    }

    @PostMapping
    public ResponseEntity<OperatorBootstrapLoginResponseDto> login(
            @Valid @RequestBody OperatorBootstrapLoginRequestDto request,
            HttpServletRequest httpRequest) {
        if (DemoSessionRequestSupport.isDemoSessionRequest(
                httpRequest)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Operator bootstrap login cannot use a demo session.");
        }
        if (!OperatorBootstrapTransportSecurity.isAllowed(
                httpRequest,
                config)) {
            throw new ResponseStatusException(
                    HttpStatus.UPGRADE_REQUIRED,
                    "HTTPS is required for operator bootstrap login.");
        }
        OperatorBootstrapAuthenticationService.LoginResult result =
                service.login(request.email(), request.password());
        if (loginSessions != null) loginSessions.recordBootstrapLogin(result.email(), httpRequest);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(new OperatorBootstrapLoginResponseDto(
                        result.tokenType(),
                        result.accessToken(),
                        result.email(),
                        result.expiresAt().toString()));
    }
}
