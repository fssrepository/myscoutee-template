package com.raxim.myscoutee.domain.platform.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.ConfigurationTestRequestDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.ConfigurationTestResultDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.OperatorConfigurationDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.OperatorConfigurationUpdateDto;
import com.raxim.myscoutee.domain.platform.api.DeploymentConfigurationManagement;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/operator/configuration")
@PreAuthorize("hasAuthority('O')")
public class OperatorConfigurationController {
    private final DeploymentConfigurationManagement service;

    public OperatorConfigurationController(DeploymentConfigurationManagement service) {
        this.service = service;
    }

    @GetMapping
    public OperatorConfigurationDto configuration() {
        return service.configuration();
    }

    @PutMapping
    public OperatorConfigurationDto update(
            @Valid @RequestBody OperatorConfigurationUpdateDto request) {
        return service.update(request);
    }

    @PostMapping("/tests")
    public ConfigurationTestResultDto test(
            @Valid @RequestBody ConfigurationTestRequestDto request) {
        return service.test(request);
    }

    @PostMapping("/firebase/activate")
    public OperatorConfigurationDto activateFirebase() {
        return service.activateFirebase();
    }
}
