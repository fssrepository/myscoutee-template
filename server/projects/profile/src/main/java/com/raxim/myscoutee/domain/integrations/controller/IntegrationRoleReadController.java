package com.raxim.myscoutee.domain.integrations.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.raxim.myscoutee.domain.integrations.service.AdminClientService;
import com.raxim.myscoutee.domain.integrations.service.IntegrationCapabilitiesService;

/** Delegates to the existing bounded report readers; exposes no role management commands. */
@RestController
@RequestMapping("/integrations/v1/query")
@PreAuthorize("hasAuthority('INTEGRATION')")
public class IntegrationRoleReadController {
    private final IntegrationCapabilitiesService access;
    private final AdminClientService admin;

    public IntegrationRoleReadController(IntegrationCapabilitiesService access, AdminClientService admin) {
        this.access = access; this.admin = admin;
    }

    @GetMapping("/admin/overview")
    public AdminClientService.Overview adminOverview(Authentication authentication) {
        return admin.overview(access.requireRoleRead(authentication, "admin"));
    }

}
