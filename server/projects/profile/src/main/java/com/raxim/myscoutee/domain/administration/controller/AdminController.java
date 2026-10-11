package com.raxim.myscoutee.domain.administration.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.*;
import com.raxim.myscoutee.domain.content.api.AdministrativeAccess;
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAuthority('A')")
public class AdminController {
    private final AdministrativeAccess adminModerationService;
    public AdminController(AdministrativeAccess moderation) { this.adminModerationService = moderation; }
    @GetMapping("/dashboard")
    public AdminDashboardDto dashboard(
            @RequestParam(value = "adminUserId", required = false) String adminUserId,
            @RequestParam(value = "reportsStatus", required = false) String reportsStatus,
            @RequestParam(value = "feedbackStatus", required = false) String feedbackStatus) {
        return adminModerationService.dashboard(adminUserId, reportsStatus, feedbackStatus);
    }
    @PostMapping("/users/{userId}/warn")
    public AdminDashboardDto warn(@PathVariable String userId, @RequestBody(required = false) AdminActionRequestDto request) {
        return adminModerationService.warn(userId, request);
    }
    @PostMapping("/users/{userId}/block")
    public AdminDashboardDto block(@PathVariable String userId, @RequestBody(required = false) AdminActionRequestDto request) {
        return adminModerationService.block(userId, request);
    }
    @PostMapping("/users/{userId}/unblock")
    public AdminDashboardDto unblock(@PathVariable String userId, @RequestBody(required = false) AdminActionRequestDto request) {
        return adminModerationService.unblock(userId, request);
    }
    @PostMapping("/reports/{reportId}/resolve")
    public AdminDashboardDto resolveReport(
            @PathVariable String reportId,
            @RequestBody(required = false) AdminReviewResolveRequestDto request) {
        return adminModerationService.resolveReport(reportId, request);
    }
    @PostMapping("/feedback/{feedbackId}/resolve")
    public AdminDashboardDto resolveFeedback(
            @PathVariable String feedbackId,
            @RequestBody(required = false) AdminReviewResolveRequestDto request) {
        return adminModerationService.resolveFeedback(feedbackId, request);
    }
}
