package com.raxim.myscoutee.domain.notifications.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notificationRuleConfigs")
@lombok.Getter @lombok.Setter
public class NotificationRuleConfig implements com.raxim.myscoutee.domain.notifications.api.NotificationJobs.Configuration {
    @Id
    private String id;

    @Indexed(name = "notification_rule_config_sync_key", unique = true, sparse = true)
    private String syncKey;

    private String ruleKey;
    private String baseGroupId;

    private String label;
    private String category;
    private String description;
    private String actionKey;
    private String triggerKind;
    private Boolean enabled;
    private Boolean manualRunEnabled;
    private Boolean adminManageable;
    private Integer priority;
    private DeliveryChannels channels = new DeliveryChannels();
    private Timing timing = new Timing();
    private List<ScheduleSlot> scheduleSlots = new ArrayList<>();
    private List<ParameterField> parameters = new ArrayList<>();
    private MessageTemplate message = new MessageTemplate();
    private RunState runState = new RunState();
    private List<RunHistoryEntry> runHistory = new ArrayList<>();
    private String createdDate;
    private String createdUser;
    private String updatedDate;
    private String updatedUser;

    public void setChannels(DeliveryChannels channels) {
        this.channels = channels == null ? new DeliveryChannels() : channels;
    }

    public void setTiming(Timing timing) {
        this.timing = timing == null ? new Timing() : timing;
    }

    public void setScheduleSlots(List<ScheduleSlot> scheduleSlots) {
        this.scheduleSlots = scheduleSlots == null ? new ArrayList<>() : scheduleSlots;
    }

    public void setParameters(List<ParameterField> parameters) {
        this.parameters = parameters == null ? new ArrayList<>() : parameters;
    }

    public void setMessage(MessageTemplate message) {
        this.message = message == null ? new MessageTemplate() : message;
    }

    public void setRunState(RunState runState) {
        this.runState = runState == null ? new RunState() : runState;
    }

    public void setRunHistory(List<RunHistoryEntry> runHistory) {
        this.runHistory = runHistory == null ? new ArrayList<>() : runHistory;
    }

    @lombok.Getter @lombok.Setter
    public static class DeliveryChannels {
        private Boolean pushEnabled;
        private Boolean emailEnabled;
        private Boolean inAppEnabled;
        private Boolean supportChatEnabled;

    }

    @lombok.Getter @lombok.Setter
    public static class Timing {
        private String mode;
        private Integer delayMinutes;
        private Integer intervalMinutes;
        private Integer intervalSeconds;
        private Integer intervalAmount;
        private String intervalUnit;
        private Integer month;
        private Integer dayOfMonth;
        private String time;
        private String timezone;
        private String cronExpression;

    }

    @lombok.Getter @lombok.Setter
    public static class ScheduleSlot {
        private String id;
        private String frequency;
        private String date;
        private Integer dayOfWeek;
        private String time;
        private String timezone;
        private String cronExpression;
        private String actionKey;
        private Boolean enabled;

    }

    @lombok.Getter @lombok.Setter
    public static class ParameterField implements com.raxim.myscoutee.domain.notifications.api.NotificationJobs.Parameter {
        private String key;
        private String label;
        private String labelKey;
        private String group;
        private String groupKey;
        private String valueType;
        private Double numberValue;
        private String textValue;
        private String unit;
        private List<ParameterOption> options = new ArrayList<>();
        private String strategy;
        private String strategyKey;
        private Boolean readOnly;

        public void setOptions(List<ParameterOption> options) { this.options = options == null ? new ArrayList<>() : options; }
    }

    @lombok.Getter @lombok.Setter
    public static class ParameterOption {
        private String value;
        private String label;
        private String labelKey;

    }

    @lombok.Getter @lombok.Setter
    public static class MessageTemplate {
        private String pushTitle;
        private String pushBody;
        private String emailTemplateKey;
        private String emailSubject;
        private String emailBody;
        private String ctaPath;

    }

    @lombok.Getter @lombok.Setter
    public static class RunState {
        private String currentStatus;
        private Integer progressPercent;
        private String progressDetail;
        private String startedAtIso;
        private String finishedAtIso;
        private Long durationMillis;
        private String lastRunAtIso;
        private String lastRunStatus;
        private String lastRunDetail;
        private Integer lastRunCount;
        private String lastRunUser;
        private String pendingClaimStartedAtIso;
        private Integer pendingClaimedCount;
        private Integer pendingCount = 0;
        private String pendingCountUnit;
        private String pendingCountUpdatedAtIso;

    }

    @lombok.Getter @lombok.Setter
    public static class RunHistoryEntry {
        private String id;
        private String trigger;
        private String runnerUser;
        private String startedAtIso;
        private String finishedAtIso;
        private Long durationMillis;
        private Integer processedCount;
        private String status;
        private String detail;

    }
}
