package com.raxim.myscoutee.domain.accounts.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.FacebookDataDeletionResponseDto;
import com.raxim.myscoutee.domain.accounts.service.FacebookDataDeletionService;

@RestController
@RequestMapping("/auth/facebook")
public class FacebookDataDeletionController {
    private final FacebookDataDeletionService facebookDataDeletionService;

    public FacebookDataDeletionController(FacebookDataDeletionService facebookDataDeletionService) {
        this.facebookDataDeletionService = facebookDataDeletionService;
    }

    @PostMapping("/data-deletion")
    public FacebookDataDeletionResponseDto deleteFacebookUserData(
            @RequestParam("signed_request") String signedRequest) {
        return facebookDataDeletionService.handleSignedRequest(signedRequest);
    }

    @GetMapping(value = "/data-deletion/status", produces = MediaType.TEXT_HTML_VALUE)
    public String facebookUserDataDeletionStatus(
            @RequestParam(value = "code", required = false) String confirmationCode) {
        return facebookDataDeletionService.renderStatusPage(confirmationCode);
    }
}
