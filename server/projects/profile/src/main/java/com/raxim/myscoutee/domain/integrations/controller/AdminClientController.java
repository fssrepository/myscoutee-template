package com.raxim.myscoutee.domain.integrations.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.raxim.myscoutee.domain.integrations.service.AdminClientService;

@RestController
@RequestMapping("/admin-client/v1")
@PreAuthorize("hasAuthority('ADMIN_CLIENT')")
public class AdminClientController {
    private final AdminClientService service;
    public AdminClientController(AdminClientService service) { this.service = service; }
    @GetMapping("/overview") public ResponseEntity<AdminClientService.Overview> overview(Authentication authentication) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.overview(authentication.getName()));
    }
}
