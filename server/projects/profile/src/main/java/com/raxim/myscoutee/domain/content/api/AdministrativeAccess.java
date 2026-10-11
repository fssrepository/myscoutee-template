package com.raxim.myscoutee.domain.content.api;

import java.util.*;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.*;
public interface AdministrativeAccess {
    AdminDashboardDto dashboard(String adminUserId);
    AdminDashboardDto dashboard(String adminUserId, String reportsStatus, String feedbackStatus);
    AdminUserDto resolveAdminActor(String adminUserId);
    AdminDashboardDto warn(String userId, AdminActionRequestDto request);
    AdminDashboardDto block(String userId, AdminActionRequestDto request);
    AdminDashboardDto unblock(String userId, AdminActionRequestDto request);
    AdminDashboardDto resolveReport(String reportId, AdminReviewResolveRequestDto request);
    AdminDashboardDto resolveFeedback(String feedbackId, AdminReviewResolveRequestDto request);
}
