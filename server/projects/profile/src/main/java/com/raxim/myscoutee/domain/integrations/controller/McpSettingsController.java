package com.raxim.myscoutee.domain.integrations.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.raxim.myscoutee.domain.integrations.dto.McpDtos;
import com.raxim.myscoutee.domain.access.api.AccessPermissions;
import com.raxim.myscoutee.domain.integrations.service.McpAuthorizationService;

@RestController
@RequestMapping("/integrations/mcp")
public class McpSettingsController {
    private final McpAuthorizationService service;
    private final AccessPermissions access;
    public McpSettingsController(McpAuthorizationService service, AccessPermissions access) {
        this.service = service; this.access = access;
    }
    @GetMapping("/settings")
    public McpDtos.Settings settings(Authentication auth) { return service.settings(access.resolveActorUserId(auth)); }
    @PostMapping("/clients")
    public McpDtos.Created create(Authentication auth, @RequestBody McpDtos.ClientRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return service.create(access.resolveActorUserId(auth), request);
    }
    @DeleteMapping("/clients/{id}")
    public void revoke(Authentication auth, @PathVariable String id) { service.revoke(access.resolveActorUserId(auth), id); }
    @PostMapping("/authorization")
    public McpDtos.Context context(Authentication auth, @RequestBody McpDtos.Authorization request) {
        return service.context(access.resolveActorUserId(auth), request);
    }
    @PostMapping("/consent")
    public McpDtos.Redirect consent(Authentication auth, @RequestBody McpDtos.Consent request,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return service.authorize(access.resolveActorUserId(auth), request);
    }
}
