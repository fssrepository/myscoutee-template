package com.raxim.myscoutee.domain.integrations.controller;

import com.raxim.myscoutee.domain.integrations.service.AdminClientAccessService;
import com.raxim.myscoutee.domain.integrations.service.IntegrationSettingsService;
import com.raxim.myscoutee.domain.integrations.service.IntegrationTokenService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;

import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.*;

@RestController
@RequestMapping("/admin/client-api")
public class AdminClientSettingsController {
    private final AccessPermissions actors;
    private final AdminClientAccessService access;
    private final IntegrationTokenService tokens;
    private final IntegrationSettingsService settings;
    public AdminClientSettingsController(AccessPermissions actors, AdminClientAccessService access, IntegrationTokenService tokens, IntegrationSettingsService settings) {
        this.actors = actors; this.access = access; this.tokens = tokens; this.settings = settings;
    }
    private String owner(Authentication authentication) {
        String id = actors.resolveActorUserId(authentication); access.requireAdmin(id); return id;
    }
    @GetMapping("/settings") public IntegrationSettingsDto settings(Authentication authentication) {
        return settings.load(owner(authentication), true);
    }
    @PutMapping("/settings") public IntegrationSettingsDto save(Authentication authentication, @RequestBody IntegrationSettingsUpdateDto request) {
        return settings.save(owner(authentication), request, true);
    }
    @PostMapping("/tokens") public IntegrationTokenCreatedDto create(Authentication authentication, @RequestBody IntegrationTokenCreateRequestDto request) {
        return tokens.create(owner(authentication), request, IntegrationTokenService.ADMIN_SCOPE);
    }
    @DeleteMapping("/tokens/{id}") public void revoke(Authentication authentication, @PathVariable String id) {
        tokens.revoke(owner(authentication), id, IntegrationTokenService.ADMIN_SCOPE);
    }
}
