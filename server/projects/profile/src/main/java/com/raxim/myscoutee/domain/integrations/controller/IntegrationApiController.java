package com.raxim.myscoutee.domain.integrations.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.raxim.myscoutee.domain.integrations.dto.IntegrationDtos.IntegrationConnectDto;
import com.raxim.myscoutee.domain.integrations.service.IntegrationCapabilitiesService;

@RestController
@RequestMapping("/integrations/v1")
public class IntegrationApiController {
    private final IntegrationCapabilitiesService capabilities;
    public IntegrationApiController(IntegrationCapabilitiesService capabilities) { this.capabilities = capabilities; }

    @PostMapping("/connect")
    public IntegrationConnectDto connect(Authentication authentication, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return capabilities.describe(authentication);
    }

}
