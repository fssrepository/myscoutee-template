package com.raxim.myscoutee.domain.notifications.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.raxim.myscoutee.domain.platform.api.OperatorNotificationSettings;
import com.raxim.myscoutee.domain.notifications.api.RoleNotifications;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery;
import com.raxim.myscoutee.domain.accounts.api.AccountNotifications;
import com.raxim.myscoutee.domain.platform.api.AdministratorSettings;
import com.raxim.myscoutee.domain.notifications.api.NotificationDelivery.NotificationIntent;

/** Role events use the same durable outbox, inbox counters and delivery preferences as member events. */
@Service
public class RoleNotificationService implements RoleNotifications {
    private final AccountNotifications accounts;
    private final OperatorNotificationSettings config;
    private final AdministratorSettings settings;
    private final NotificationDelivery outbox;

    public RoleNotificationService(AccountNotifications accounts, OperatorNotificationSettings config,
            AdministratorSettings settings, NotificationDelivery outbox) {
        this.accounts = accounts;
        this.config = config;
        this.settings = settings;
        this.outbox = outbox;
    }

    public void releaseAvailable(String version) {
        publish("release-available", version, true, false, "New version",
                "Version " + version + " is available. Review it before installing.",
                Map.of("releaseVersion", version), "updates", null);
    }

    public void moderationSubmitted(String kind, String submissionId) {
        boolean report = "report-submitted".equals(kind);
        if (!report && !"feedback-submitted".equals(kind)) throw new IllegalArgumentException("Unknown moderation event.");
        publish(kind, submissionId, false, true, report ? "New report" : "New feedback",
                report ? "A report is waiting for review." : "A member sent feedback.", Map.of(), null,
                report ? "reports" : "feedback");
    }

    private void publish(String kind, String eventId, boolean operators, boolean admins,
            String title, String body, Map<String, String> parameters, String operatorTarget, String adminTarget) {
        if (!StringUtils.hasText(eventId)) return;
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (operators) {
            var operatorSettings = config.operatorNotificationRecipients();
            operatorSettings.users().forEach(email -> emails.add(email.trim().toLowerCase(Locale.ROOT)));
            if (StringUtils.hasText(operatorSettings.bootstrapEmail())) {
                emails.add(operatorSettings.bootstrapEmail().trim().toLowerCase(Locale.ROOT));
            }
        }
        if (admins) emails.addAll(settings.adminEmailsAmong(settings.adminEmails()));
        if (emails.isEmpty()) return;
        // One bounded recipient lookup, not a users collection scan or a query per recipient.
        var recipients = accounts.roleRecipients(emails);
        var adminRecipients = admins ? settings.adminEmailsAmong(recipients.stream()
                .map(AccountNotifications.RoleRecipient::email).distinct().toList()) : java.util.Set.<String>of();
        for (var recipient : recipients) {
            boolean operator = operators && recipient.operator();
            if (!operator && (!admins || recipient.email() == null || !adminRecipients.contains(recipient.email()))) continue;
            String target = operator ? operatorTarget : adminTarget;
            String path = operator ? "/operator" : "/admin";
            var payload = new java.util.LinkedHashMap<>(parameters);
            payload.put("notification_title_key", "notification.kind." + kind + ".title");
            payload.put("notification_message_key", "notification.kind." + kind + ".message");
            payload.put("notification_tone", "info");
            if (target != null) payload.put("roleTarget", target);
            if (target != null) path += "?notificationTarget=" + target;
            outbox.enqueueOnce("role:" + kind + ":" + eventId + ":" + recipient.userId(), new NotificationIntent(
                    kind, operator ? "operator" : "app-admin", eventId,
                    operator ? "app-admin:operator" : "app-admin", List.of(recipient.userId()),
                    title, body, path, null, null, kind + "-" + eventId, payload, null));
        }
    }
}
