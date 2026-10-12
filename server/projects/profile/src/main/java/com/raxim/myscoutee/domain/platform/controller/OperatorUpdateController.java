package com.raxim.myscoutee.domain.platform.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.raxim.myscoutee.domain.accounts.api.AccountIdentity;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateApprovalDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateEventPageDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateJobDto;
import com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorUpdateOverviewDto;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateService;
import com.raxim.myscoutee.domain.platform.service.OperatorUpdateReleaseService;

@RestController
@RequestMapping("/operator/updates")
@PreAuthorize("hasAuthority('O')")
public class OperatorUpdateController {
    private final OperatorUpdateService service;
    private final OperatorUpdateReleaseService releases;

    public OperatorUpdateController(OperatorUpdateService service,
            OperatorUpdateReleaseService releases) {
        this.service = service;
        this.releases = releases;
    }

    @GetMapping
    public OperatorUpdateOverviewDto overview() {
        return service.overview();
    }

    @GetMapping("/releases")
    public OperatorUpdateReleaseService.ReleasePage releases() {
        return releases.releases();
    }

    @GetMapping("/releases/{version}")
    public com.raxim.myscoutee.domain.platform.dto.OperatorUpdateDtos.OperatorAnnouncementDto release(
            @PathVariable String version) {
        return releases.findVersion(version);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OperatorUpdateJobDto approve(
            @RequestBody OperatorUpdateApprovalDto approval,
            Authentication authentication) {
        return service.approve(approval, actorUserId(authentication));
    }

    @PostMapping("/jobs/{jobId}/rollback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OperatorUpdateJobDto rollback(@PathVariable String jobId, Authentication authentication) {
        return service.rollback(jobId, actorUserId(authentication));
    }

    @GetMapping("/jobs/{jobId}")
    public OperatorUpdateJobDto status(@PathVariable String jobId) {
        return service.status(jobId);
    }

    @GetMapping("/jobs/{jobId}/events")
    public OperatorUpdateEventPageDto events(
            @PathVariable String jobId,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return service.events(jobId, after, limit);
    }

    private String actorUserId(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AccountIdentity principal) {
            return principal.getUserId();
        }
        return authentication == null ? null : authentication.getName();
    }
}
