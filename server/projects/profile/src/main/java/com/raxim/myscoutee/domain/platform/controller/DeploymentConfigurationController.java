package com.raxim.myscoutee.domain.platform.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.DeploymentPublicConfigurationDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.FirebasePublicConfigurationDto;
import com.raxim.myscoutee.domain.platform.service.OperatorConfigurationService;

@RestController
@RequestMapping("/deployment/configuration")
public class DeploymentConfigurationController {
    private final OperatorConfigurationService service;

    public DeploymentConfigurationController(
            OperatorConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    public DeploymentPublicConfigurationDto configuration() {
        return service.publicConfiguration();
    }

    @GetMapping("/firebase-config")
    public ResponseEntity<FirebasePublicConfigurationDto>
            firebaseConfiguration() {
        FirebasePublicConfigurationDto configuration =
                service.publicFirebaseConfiguration();
        return ResponseEntity.status(configuration == null
                        ? HttpStatus.NO_CONTENT : HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(configuration);
    }
}
