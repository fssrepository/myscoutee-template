package com.raxim.myscoutee.domain.content.service;


import com.raxim.myscoutee.domain.accounts.api.SupportCaseCounters;
import com.raxim.myscoutee.domain.administration.api.OperationalTelemetry;
import com.raxim.myscoutee.domain.chat.api.SupportMessages;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.chat.api.ChatDirectory.Chat;
import com.raxim.myscoutee.domain.content.document.UserFeedbackSubmission;
import com.raxim.myscoutee.domain.content.document.UserReportSubmission;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummary;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminActionRequestDto;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminDashboardDto;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminFeedbackDto;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminReportDto;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminReportedUserDto;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminReviewCountsDto;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminReviewResolveRequestDto;
import com.raxim.myscoutee.domain.administration.dto.AdminDtos.AdminUserDto;
import com.raxim.myscoutee.domain.chat.api.ChatReview;
import com.raxim.myscoutee.domain.chat.api.ChatDirectory;
import com.raxim.myscoutee.domain.content.repository.UserFeedbackSubmissionsRepository;
import com.raxim.myscoutee.domain.content.repository.UserReportSubmissionsRepository;
import com.raxim.myscoutee.domain.accounts.api.ProfileSummaries;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.NotificationIntent;

@Service
public class AdminModerationService implements com.raxim.myscoutee.domain.content.api.AdministrativeAccess {
    private final com.raxim.myscoutee.domain.administration.api.AdministrativeActors adminActors;

    @org.springframework.beans.factory.annotation.Autowired private com.raxim.myscoutee.domain.accounts.api.ProfileModeration profileModeration;
    private final UserReportSubmissionsRepository reportsRepository;
    private final UserFeedbackSubmissionsRepository feedbackRepository;
    private final ProfileSummaries usersRepository;
    private final ChatDirectory chatsRepository;
    private final ChatReview chatMessagesRepository;
    private final SupportMessages chatService;
    private final SupportCaseCounters supportCaseCounters;
    private final OperationalTelemetry adminTelemetryService;
    private final NotificationDelivery notificationOutboxService;

    public AdminModerationService(
            com.raxim.myscoutee.domain.administration.api.AdministrativeActors adminActors,
            UserReportSubmissionsRepository reportsRepository,
            UserFeedbackSubmissionsRepository feedbackRepository,
            ProfileSummaries usersRepository,
            ChatDirectory chatsRepository,
            ChatReview chatMessagesRepository,
            SupportMessages chatService,
            SupportCaseCounters supportCaseCounters,
            OperationalTelemetry adminTelemetryService,
            NotificationDelivery notificationOutboxService) {
        this.adminActors = adminActors;
        this.reportsRepository = reportsRepository;
        this.feedbackRepository = feedbackRepository;
        this.usersRepository = usersRepository;
        this.chatsRepository = chatsRepository;
        this.chatMessagesRepository = chatMessagesRepository;
        this.chatService = chatService;
        this.supportCaseCounters = supportCaseCounters;
        this.adminTelemetryService = adminTelemetryService;
        this.notificationOutboxService = notificationOutboxService;
    }

    public AdminDashboardDto dashboard(String adminUserId) {
        return dashboard(adminUserId, null, null);
    }

    public AdminDashboardDto dashboard(String adminUserId, String reportsStatus, String feedbackStatus) {
        AdminUserDto admin = adminActors.resolveAdminActor(adminUserId);
        ensureStoredSupportCaseCounters(admin.id());
        return dashboardFor(
                admin,
                normalizeReviewStatus(reportsStatus),
                normalizeReviewStatus(feedbackStatus));
    }

    private void ensureStoredSupportCaseCounters(String adminUserId) {
        if (!supportCaseCounters.hasSupportCaseCounters(normalize(adminUserId))) {
            supportCaseCounters.refreshSupportCaseCountersForAdmins();
        }
    }

    public AdminUserDto resolveAdminActor(String adminUserId) {
        return adminActors.resolveAdminActor(adminUserId);
    }

    private AdminDashboardDto dashboardFor(AdminUserDto admin) {
        return dashboardFor(admin, null, null);
    }

    private AdminDashboardDto dashboardFor(AdminUserDto admin, String reportsStatus, String feedbackStatus) {
        List<UserReportSubmission> allReports = reportsRepository.findAllByOrderByCreatedDateDesc();
        List<UserFeedbackSubmission> allFeedback = feedbackRepository.findAllByOrderByCreatedDateDesc();
        Map<String, ProfileSummary> scopedUsers = usersById(allReports, allFeedback);
        String groupId = usersRepository.findByUserId(admin.id()).map(ProfileSummary::getWorkspaceGroupId).orElse(null);
        var blocked = usersRepository.findByProfileStatus("blocked");
        blocked.forEach(user -> scopedUsers.put(user.getUserId(), user));
        java.util.function.Predicate<String> inScope = id -> java.util.Objects.equals(groupId,
                reportWorkspace(scopedUsers.get(id)));
        allReports = allReports.stream().filter(report -> inScope.test(report.getUserId())).toList();
        allFeedback = allFeedback.stream().filter(item -> inScope.test(item.getUserId())).toList();
        List<UserReportSubmission> reports = filterReportsByStatus(allReports, reportsStatus);
        List<UserFeedbackSubmission> feedback = filterFeedbackByStatus(allFeedback, feedbackStatus);
        Map<String, ProfileSummary> usersById = scopedUsers;
        Map<String, List<UserReportSubmission>> byTarget = new LinkedHashMap<>();
        for (UserReportSubmission report : reports) {
            String targetUserId = normalize(report.getTargetUserId());
            if (StringUtils.hasText(targetUserId)) {
                byTarget.computeIfAbsent(targetUserId, ignored -> new ArrayList<>()).add(report);
            }
        }
        Map<String, List<UserReportSubmission>> allByTarget = new LinkedHashMap<>();
        for (UserReportSubmission report : allReports) {
            String targetUserId = normalize(report.getTargetUserId());
            if (StringUtils.hasText(targetUserId)) {
                allByTarget.computeIfAbsent(targetUserId, ignored -> new ArrayList<>()).add(report);
            }
        }
        List<AdminReportedUserDto> reportedUsers = byTarget.entrySet().stream()
                .map(entry -> toReportedUser(entry.getKey(), entry.getValue(), usersById, admin.id()))
                .sorted(Comparator.comparing(AdminReportedUserDto::lastReportedAtIso, Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
        List<AdminReportedUserDto> blockedUsers = blocked.stream()
                .filter(user -> java.util.Objects.equals(groupId, reportWorkspace(user)))
                .map(user -> toBlockedUser(user, allByTarget.getOrDefault(normalize(user.getUserId()), List.of()), usersById, admin.id()))
                .toList();
        return new AdminDashboardDto(
                admin,
                reportedUsers,
                blockedUsers,
                feedback.stream().map(item -> toFeedback(item, usersById)).toList(),
                reviewCounts(allReports, allFeedback));
    }

    public AdminDashboardDto warn(String userId, AdminActionRequestDto request) {
        String targetUserId = normalize(userId);
        ensureReportedUser(targetUserId);
        AdminUserDto admin = resolveAdmin(request == null ? null : request.adminUserId());
        UserReportSubmission report = requireWarningReport(
                targetUserId,
                request == null ? null : request.reportId());
        requireScope(admin.id(), report == null ? targetUserId : report.getUserId());
        String message = normalize(request == null ? null : request.message());
        appendSupportMessage(targetUserId, admin, StringUtils.hasText(message) ? message
                : "Please update the reported behavior before your account is blocked.", "warned");
        markReportWarned(report, admin.id());
        adminTelemetryService.record("admin.user.warned", "moderation", targetUserId, admin.id());
        return dashboard(admin.id());
    }

    public AdminDashboardDto block(String userId, AdminActionRequestDto request) {
        String targetUserId = normalize(userId);
        ensureReportedUser(targetUserId);
        AdminUserDto admin = resolveAdmin(request == null ? null : request.adminUserId());
        ProfileSummary user = usersRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reported user not found."));
        requireScope(admin.id(), targetUserId);
        String message = normalize(request == null ? null : request.message());
        var notice = new com.raxim.myscoutee.domain.accounts.api.ProfileModeration.SupportNotice(
                admin.id(), admin.name(), admin.initials(), StringUtils.hasText(message) ? message
                : "Your account has been blocked after moderation review. You can reply here to contact support and ask for a review.");
        if (profileModeration.block(user.getUserId(), notice)) {
            adminTelemetryService.record("admin.user.blocked", "moderation", targetUserId, admin.id());
        }
        return dashboard(admin.id());
    }

    public AdminDashboardDto unblock(String userId, AdminActionRequestDto request) {
        String targetUserId = normalize(userId);
        AdminUserDto admin = resolveAdmin(request == null ? null : request.adminUserId());
        ProfileSummary user = usersRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocked user not found."));
        requireScope(admin.id(), targetUserId);
        profileModeration.unblock(user.getUserId());
        adminTelemetryService.record("admin.user.unblocked", "moderation", targetUserId, admin.id());
        return dashboard(admin.id());
    }

    public AdminDashboardDto resolveReport(String reportId, AdminReviewResolveRequestDto request) {
        String normalizedReportId = normalize(reportId);
        if (!StringUtils.hasText(normalizedReportId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report id is required.");
        }
        AdminUserDto admin = resolveAdmin(request == null ? null : request.adminUserId());
        UserReportSubmission report = reportsRepository.findById(normalizedReportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found."));
        requireScope(admin.id(), report.getUserId());
        boolean resolved = request == null || request.resolved() == null || Boolean.TRUE.equals(request.resolved());
        boolean wasResolved = isResolved(report.getResolvedAtIso());
        String nowIso = Instant.now().toString();
        if (resolved) {
            report.setResolvedAtIso(nowIso);
            report.setResolvedByAdminUserId(admin.id());
        } else {
            report.setResolvedAtIso(null);
            report.setResolvedByAdminUserId(null);
        }
        report.setUpdatedDate(nowIso);
        report.setUpdatedUser(admin.id());
        reportsRepository.save(report);
        if (resolved && !wasResolved) {
            appendSupportMessage(
                    normalize(report.getUserId()),
                    admin,
                    reportResolvedMessage(report),
                    "solved");
        }
        adminTelemetryService.record(resolved ? "admin.report.resolved" : "admin.report.unresolved", "moderation", normalizedReportId, admin.id());
        return dashboard(admin.id());
    }

    public AdminDashboardDto resolveFeedback(String feedbackId, AdminReviewResolveRequestDto request) {
        String normalizedFeedbackId = normalize(feedbackId);
        if (!StringUtils.hasText(normalizedFeedbackId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feedback id is required.");
        }
        AdminUserDto admin = resolveAdmin(request == null ? null : request.adminUserId());
        UserFeedbackSubmission feedback = feedbackRepository.findById(normalizedFeedbackId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found."));
        requireScope(admin.id(), feedback.getUserId());
        boolean resolved = request == null || request.resolved() == null || Boolean.TRUE.equals(request.resolved());
        boolean wasResolved = isResolved(feedback.getResolvedAtIso());
        String nowIso = Instant.now().toString();
        if (resolved) {
            feedback.setResolvedAtIso(nowIso);
            feedback.setResolvedByAdminUserId(admin.id());
        } else {
            feedback.setResolvedAtIso(null);
            feedback.setResolvedByAdminUserId(null);
        }
        feedback.setUpdatedDate(nowIso);
        feedback.setUpdatedUser(admin.id());
        feedbackRepository.save(feedback);
        if (resolved && !wasResolved) {
            enqueueFeedbackResolvedNotification(feedback, admin);
        }
        adminTelemetryService.record(resolved ? "admin.feedback.resolved" : "admin.feedback.unresolved", "moderation", normalizedFeedbackId, admin.id());
        return dashboard(admin.id());
    }

    private AdminUserDto resolveAdmin(String adminUserId) {
        return adminActors.resolveAdminActor(adminUserId);
    }



    /** Base-profile telemetry excludes independent workspaces; those have their own statistics. */

    private String reportWorkspace(ProfileSummary user) {
        return user == null ? null : user.getWorkspaceGroupId();
    }

    private void requireScope(String adminId, String userId) {
        ProfileSummary user = usersRepository.findByUserId(normalize(userId)).orElse(null);
        String selected = usersRepository.findByUserId(adminId).map(ProfileSummary::getWorkspaceGroupId).orElse(null);
        if (!java.util.Objects.equals(selected, reportWorkspace(user)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found in this workspace.");
    }

    private void ensureReportedUser(String targetUserId) {
        if (!StringUtils.hasText(targetUserId)
                || reportsRepository.findByTargetUserIdOrderByCreatedDateDesc(targetUserId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only reported users can be moderated.");
        }
    }

    private UserReportSubmission requireWarningReport(String targetUserId, String reportId) {
        String normalizedReportId = normalize(reportId);
        if (!StringUtils.hasText(normalizedReportId)) {
            return null;
        }
        UserReportSubmission report = reportsRepository.findById(normalizedReportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found."));
        if (!targetUserId.equals(normalize(report.getTargetUserId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report does not belong to the reported user.");
        }
        return report;
    }

    private void markReportWarned(UserReportSubmission report, String adminUserId) {
        if (report == null) {
            return;
        }
        String nowIso = Instant.now().toString();
        report.setWarnedAtIso(nowIso);
        report.setWarnedByAdminUserId(adminUserId);
        report.setUpdatedDate(nowIso);
        report.setUpdatedUser(adminUserId);
        reportsRepository.save(report);
    }

    private void appendSupportMessage(String targetUserId, AdminUserDto admin, String text, String supportStatus) {
        chatService.sendAdminSupportMessage(
                targetUserId,
                admin.id(),
                admin.name(),
                admin.initials(),
                text,
                supportStatus);
    }

    private void enqueueFeedbackResolvedNotification(UserFeedbackSubmission feedback, AdminUserDto admin) {
        String targetUserId = normalize(feedback == null ? null : feedback.getUserId());
        String feedbackId = normalize(feedback == null ? null : feedback.getId());
        if (!StringUtils.hasText(targetUserId) || !StringUtils.hasText(feedbackId)) {
            return;
        }
        String subject = normalize(feedback.getSubject());
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("feedbackId", feedbackId);
        payload.put("senderUserId", admin.id());
        payload.put("senderName", admin.name());
        payload.put("notification_tone", "success");
        payload.put("notification_status_badge_fallback", "Resolved");
        payload.put("notification_status_badge_tone", "success");
        if (StringUtils.hasText(subject)) {
            payload.put("feedbackSubject", subject);
        }
        if (admin.images() != null) {
            admin.images().stream()
                    .map(this::normalize)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .ifPresent(image -> payload.put("senderAvatarUrl", image));
        }
        notificationOutboxService.enqueue(new NotificationIntent(
                "feedback-resolved",
                "feedback",
                feedbackId,
                "feedback-submitters",
                List.of(targetUserId),
                StringUtils.hasText(subject) ? subject : "Feedback",
                feedbackResolvedMessage(feedback),
                "/home",
                payload.get("senderAvatarUrl"),
                "/assets/logo/template.svg",
                "feedback-resolved-" + feedbackId,
                payload,
                admin.id()));
    }

    private AdminReviewCountsDto reviewCounts(List<UserReportSubmission> reports, List<UserFeedbackSubmission> feedback) {
        return new AdminReviewCountsDto(
                (int) reports.stream().filter(report -> !isResolved(report.getResolvedAtIso())).count(),
                (int) reports.stream().filter(report -> isResolved(report.getResolvedAtIso())).count(),
                (int) feedback.stream().filter(item -> !isResolved(item.getResolvedAtIso())).count(),
                (int) feedback.stream().filter(item -> isResolved(item.getResolvedAtIso())).count());
    }

    private List<UserReportSubmission> filterReportsByStatus(List<UserReportSubmission> reports, String status) {
        if (!StringUtils.hasText(status)) {
            return reports;
        }
        return reports.stream()
                .filter(report -> matchesReviewStatus(report.getResolvedAtIso(), status))
                .toList();
    }

    private List<UserFeedbackSubmission> filterFeedbackByStatus(List<UserFeedbackSubmission> feedback, String status) {
        if (!StringUtils.hasText(status)) {
            return feedback;
        }
        return feedback.stream()
                .filter(item -> matchesReviewStatus(item.getResolvedAtIso(), status))
                .toList();
    }

    private boolean matchesReviewStatus(String resolvedAtIso, String status) {
        boolean resolved = isResolved(resolvedAtIso);
        return "resolved".equals(status) ? resolved : !resolved;
    }

    private boolean isResolved(String resolvedAtIso) {
        return StringUtils.hasText(normalize(resolvedAtIso));
    }

    private String normalizeReviewStatus(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        if ("resolved".equals(normalized) || "read".equals(normalized) || "solved".equals(normalized)) {
            return "resolved";
        }
        if ("unresolved".equals(normalized) || "new".equals(normalized) || "open".equals(normalized)) {
            return "unresolved";
        }
        return null;
    }

    private String reportResolvedMessage(UserReportSubmission report) {
        String handle = normalize(report.getHandle());
        if (StringUtils.hasText(handle)) {
            return "Your report about " + handle + " has been reviewed by the moderation team. Thank you for helping keep the app safe.";
        }
        return "Your report has been reviewed by the moderation team. Thank you for helping keep the app safe.";
    }

    private String feedbackResolvedMessage(UserFeedbackSubmission feedback) {
        String subject = normalize(feedback.getSubject());
        if (StringUtils.hasText(subject)) {
            return "Your feedback \"" + subject + "\" has been reviewed. Thank you for helping improve the app.";
        }
        return "Your feedback has been reviewed. Thank you for helping improve the app.";
    }

    private Map<String, ProfileSummary> usersById(List<UserReportSubmission> reports, List<UserFeedbackSubmission> feedback) {
        List<String> ids = new ArrayList<>();
        reports.forEach(report -> {
            ids.add(normalize(report.getUserId()));
            ids.add(normalize(report.getTargetUserId()));
        });
        feedback.forEach(item -> ids.add(normalize(item.getUserId())));
        Map<String, ProfileSummary> users = new LinkedHashMap<>();
        for (ProfileSummary user : usersRepository.findByUserIdIn(ids.stream().filter(StringUtils::hasText).distinct().toList())) {
            users.put(user.getUserId(), user);
        }
        return users;
    }

    private AdminReportedUserDto toReportedUser(String userId, List<UserReportSubmission> reports, Map<String, ProfileSummary> usersById, String adminUserId) {
        ProfileSummary user = usersById.get(userId);
        Chat supportChat = supportChat(adminUserId, userId);
        return new AdminReportedUserDto(
                userId,
                text(user == null ? null : user.getName(), "Reported user"),
                text(user == null ? null : user.getInitials(), "??"),
                text(user == null ? null : user.getGender(), "woman"),
                text(user == null ? null : user.getCity(), ""),
                firstImage(user),
                text(user == null ? null : user.getProfileStatus(), "public"),
                reports.size(),
                reports.isEmpty() ? null : reports.get(0).getCreatedDate(),
                supportChat != null,
                supportChat == null || supportChat.getUnread() == null ? 0 : Math.max(0, supportChat.getUnread()),
                supportChat == null ? null : supportChat.getChatId(),
                reports.stream().map(report -> toReport(report, usersById)).toList());
    }

    private AdminReportedUserDto toBlockedUser(ProfileSummary user, List<UserReportSubmission> reports, Map<String, ProfileSummary> usersById, String adminUserId) {
        Chat supportChat = supportChat(adminUserId, user.getUserId());
        return new AdminReportedUserDto(
                text(user.getUserId(), ""),
                text(user.getName(), "Blocked user"),
                text(user.getInitials(), "??"),
                text(user.getGender(), "woman"),
                text(user.getCity(), ""),
                firstImage(user),
                text(user.getProfileStatus(), "blocked"),
                reports.size(),
                reports.isEmpty() ? null : reports.get(0).getCreatedDate(),
                supportChat != null,
                supportChat == null || supportChat.getUnread() == null ? 0 : Math.max(0, supportChat.getUnread()),
                supportChat == null ? null : supportChat.getChatId(),
                reports.stream().map(report -> toReport(report, usersById)).toList());
    }

    private Chat supportChat(String adminUserId, String targetUserId) {
        String adminId = normalize(adminUserId);
        String userId = normalize(targetUserId);
        if (!StringUtils.hasText(adminId) || !StringUtils.hasText(userId)) {
            return null;
        }
        String chatId = "c-support-admin-" + userId;
        return chatsRepository.findByOwnerUserIdAndChatId(adminId, chatId).<Chat>map(chat -> chat)
                .or(() -> chatsRepository.findByChatId(chatId).stream().<Chat>map(chat -> chat).findFirst())
                .orElse(null);
    }

    private AdminReportDto toReport(UserReportSubmission report, Map<String, ProfileSummary> usersById) {
        ProfileSummary reporter = usersById.get(normalize(report.getUserId()));
        var chat = chatMessagesRepository.reported(report.getChatId(), report.getMessageId(), report.getUserId(), report.getTargetUserId());
        return new AdminReportDto(report.getId(), report.getUserId(), text(reporter == null ? null : reporter.getName(), "Reporter"),
                firstImage(reporter), report.getTargetUserId(), report.getHandle(), report.getReason(), report.getDetails(), report.getEventId(), report.getEventTitle(),
                report.getEventStartAtIso(), report.getMemberEntryId(), report.getSourceType(), report.getSourceId(), report.getSourceText(),
                report.getChatId(), report.getMessageId(), report.getAssetId(), report.getAssetType(),
                chat == null ? null : chat.title(),
                chat == null ? List.of() : chat.messages(),
                report.getCreatedDate(),
                report.getWarnedAtIso(),
                report.getWarnedByAdminUserId(),
                report.getResolvedAtIso(),
                report.getResolvedByAdminUserId());
    }

    private AdminFeedbackDto toFeedback(UserFeedbackSubmission item, Map<String, ProfileSummary> usersById) {
        ProfileSummary user = usersById.get(normalize(item.getUserId()));
        return new AdminFeedbackDto(item.getId(), item.getUserId(), text(user == null ? null : user.getName(), "User"),
                firstImage(user), item.getCategory(), item.getSubject(), item.getDetails(), item.getCreatedDate(),
                item.getResolvedAtIso(), item.getResolvedByAdminUserId());
    }

    private String firstImage(ProfileSummary user) {
        if (user == null || user.getImages() == null) {
            return null;
        }
        return user.getImages().stream()
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String value) { return value == null ? "" : value.trim(); }
    private String text(String value, String fallback) { return StringUtils.hasText(normalize(value)) ? normalize(value) : fallback; }

}
