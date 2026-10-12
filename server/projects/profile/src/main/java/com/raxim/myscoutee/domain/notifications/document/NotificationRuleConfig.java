package com.raxim.myscoutee.domain.notifications.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notificationRuleConfigs")
public class NotificationRuleConfig implements com.raxim.myscoutee.domain.notifications.api.NotificationJobs.Configuration {
    @Id
    private String id;

    @Indexed(name = "notification_rule_config_sync_key", unique = true, sparse = true)
    private String syncKey;

    private String ruleKey;
    private String baseGroupId;

    public String getBaseGroupId() { return baseGroupId; }
    public void setBaseGroupId(String baseGroupId) { this.baseGroupId = baseGroupId; }

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSyncKey() {
        return syncKey;
    }

    public void setSyncKey(String syncKey) {
        this.syncKey = syncKey;
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActionKey() {
        return actionKey;
    }

    public void setActionKey(String actionKey) {
        this.actionKey = actionKey;
    }

    public String getTriggerKind() {
        return triggerKind;
    }

    public void setTriggerKind(String triggerKind) {
        this.triggerKind = triggerKind;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getManualRunEnabled() {
        return manualRunEnabled;
    }

    public void setManualRunEnabled(Boolean manualRunEnabled) {
        this.manualRunEnabled = manualRunEnabled;
    }

    public Boolean getAdminManageable() {
        return adminManageable;
    }

    public void setAdminManageable(Boolean adminManageable) {
        this.adminManageable = adminManageable;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public DeliveryChannels getChannels() {
        return channels;
    }

    public void setChannels(DeliveryChannels channels) {
        this.channels = channels == null ? new DeliveryChannels() : channels;
    }

    public Timing getTiming() {
        return timing;
    }

    public void setTiming(Timing timing) {
        this.timing = timing == null ? new Timing() : timing;
    }

    public List<ScheduleSlot> getScheduleSlots() {
        return scheduleSlots;
    }

    public void setScheduleSlots(List<ScheduleSlot> scheduleSlots) {
        this.scheduleSlots = scheduleSlots == null ? new ArrayList<>() : scheduleSlots;
    }

    public List<ParameterField> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterField> parameters) {
        this.parameters = parameters == null ? new ArrayList<>() : parameters;
    }

    public MessageTemplate getMessage() {
        return message;
    }

    public void setMessage(MessageTemplate message) {
        this.message = message == null ? new MessageTemplate() : message;
    }

    public RunState getRunState() {
        return runState;
    }

    public void setRunState(RunState runState) {
        this.runState = runState == null ? new RunState() : runState;
    }

    public List<RunHistoryEntry> getRunHistory() {
        return runHistory;
    }

    public void setRunHistory(List<RunHistoryEntry> runHistory) {
        this.runHistory = runHistory == null ? new ArrayList<>() : runHistory;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedUser() {
        return createdUser;
    }

    public void setCreatedUser(String createdUser) {
        this.createdUser = createdUser;
    }

    public String getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(String updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getUpdatedUser() {
        return updatedUser;
    }

    public void setUpdatedUser(String updatedUser) {
        this.updatedUser = updatedUser;
    }

    public static class DeliveryChannels {
        private Boolean pushEnabled;
        private Boolean emailEnabled;
        private Boolean inAppEnabled;
        private Boolean supportChatEnabled;

        public Boolean getPushEnabled() {
            return pushEnabled;
        }

        public void setPushEnabled(Boolean pushEnabled) {
            this.pushEnabled = pushEnabled;
        }

        public Boolean getEmailEnabled() {
            return emailEnabled;
        }

        public void setEmailEnabled(Boolean emailEnabled) {
            this.emailEnabled = emailEnabled;
        }

        public Boolean getInAppEnabled() {
            return inAppEnabled;
        }

        public void setInAppEnabled(Boolean inAppEnabled) {
            this.inAppEnabled = inAppEnabled;
        }

        public Boolean getSupportChatEnabled() {
            return supportChatEnabled;
        }

        public void setSupportChatEnabled(Boolean supportChatEnabled) {
            this.supportChatEnabled = supportChatEnabled;
        }
    }

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

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Integer getDelayMinutes() {
            return delayMinutes;
        }

        public void setDelayMinutes(Integer delayMinutes) {
            this.delayMinutes = delayMinutes;
        }

        public Integer getIntervalMinutes() {
            return intervalMinutes;
        }

        public void setIntervalMinutes(Integer intervalMinutes) {
            this.intervalMinutes = intervalMinutes;
        }

        public Integer getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(Integer intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }

        public Integer getIntervalAmount() {
            return intervalAmount;
        }

        public void setIntervalAmount(Integer intervalAmount) {
            this.intervalAmount = intervalAmount;
        }

        public String getIntervalUnit() {
            return intervalUnit;
        }

        public void setIntervalUnit(String intervalUnit) {
            this.intervalUnit = intervalUnit;
        }

        public Integer getMonth() {
            return month;
        }

        public void setMonth(Integer month) {
            this.month = month;
        }

        public Integer getDayOfMonth() {
            return dayOfMonth;
        }

        public void setDayOfMonth(Integer dayOfMonth) {
            this.dayOfMonth = dayOfMonth;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public String getCronExpression() {
            return cronExpression;
        }

        public void setCronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
        }
    }

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

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getFrequency() {
            return frequency;
        }

        public void setFrequency(String frequency) {
            this.frequency = frequency;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Integer getDayOfWeek() {
            return dayOfWeek;
        }

        public void setDayOfWeek(Integer dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public String getCronExpression() {
            return cronExpression;
        }

        public void setCronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
        }

        public String getActionKey() {
            return actionKey;
        }

        public void setActionKey(String actionKey) {
            this.actionKey = actionKey;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

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

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getLabelKey() { return labelKey; }
        public void setLabelKey(String labelKey) { this.labelKey = labelKey; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getGroupKey() { return groupKey; }
        public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
        public String getValueType() { return valueType; }
        public void setValueType(String valueType) { this.valueType = valueType; }
        public Double getNumberValue() { return numberValue; }
        public void setNumberValue(Double numberValue) { this.numberValue = numberValue; }
        public String getTextValue() { return textValue; }
        public void setTextValue(String textValue) { this.textValue = textValue; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public List<ParameterOption> getOptions() { return options; }
        public void setOptions(List<ParameterOption> options) { this.options = options == null ? new ArrayList<>() : options; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public String getStrategyKey() { return strategyKey; }
        public void setStrategyKey(String strategyKey) { this.strategyKey = strategyKey; }
        public Boolean getReadOnly() { return readOnly; }
        public void setReadOnly(Boolean readOnly) { this.readOnly = readOnly; }
    }

    public static class ParameterOption {
        private String value;
        private String label;
        private String labelKey;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getLabelKey() { return labelKey; }
        public void setLabelKey(String labelKey) { this.labelKey = labelKey; }
    }

    public static class MessageTemplate {
        private String pushTitle;
        private String pushBody;
        private String emailTemplateKey;
        private String emailSubject;
        private String emailBody;
        private String ctaPath;

        public String getPushTitle() {
            return pushTitle;
        }

        public void setPushTitle(String pushTitle) {
            this.pushTitle = pushTitle;
        }

        public String getPushBody() {
            return pushBody;
        }

        public void setPushBody(String pushBody) {
            this.pushBody = pushBody;
        }

        public String getEmailTemplateKey() {
            return emailTemplateKey;
        }

        public void setEmailTemplateKey(String emailTemplateKey) {
            this.emailTemplateKey = emailTemplateKey;
        }

        public String getEmailSubject() {
            return emailSubject;
        }

        public void setEmailSubject(String emailSubject) {
            this.emailSubject = emailSubject;
        }

        public String getEmailBody() {
            return emailBody;
        }

        public void setEmailBody(String emailBody) {
            this.emailBody = emailBody;
        }

        public String getCtaPath() {
            return ctaPath;
        }

        public void setCtaPath(String ctaPath) {
            this.ctaPath = ctaPath;
        }
    }

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
        public String getPendingClaimStartedAtIso() { return pendingClaimStartedAtIso; }
        public void setPendingClaimStartedAtIso(String value) { pendingClaimStartedAtIso = value; }
        public Integer getPendingClaimedCount() { return pendingClaimedCount; }
        public void setPendingClaimedCount(Integer value) { pendingClaimedCount = value; }
        private Integer pendingCount = 0;
        private String pendingCountUnit;
        private String pendingCountUpdatedAtIso;

        public String getCurrentStatus() {
            return currentStatus;
        }

        public void setCurrentStatus(String currentStatus) {
            this.currentStatus = currentStatus;
        }

        public Integer getProgressPercent() {
            return progressPercent;
        }

        public void setProgressPercent(Integer progressPercent) {
            this.progressPercent = progressPercent;
        }

        public String getProgressDetail() {
            return progressDetail;
        }

        public void setProgressDetail(String progressDetail) {
            this.progressDetail = progressDetail;
        }

        public String getStartedAtIso() {
            return startedAtIso;
        }

        public void setStartedAtIso(String startedAtIso) {
            this.startedAtIso = startedAtIso;
        }

        public String getFinishedAtIso() {
            return finishedAtIso;
        }

        public void setFinishedAtIso(String finishedAtIso) {
            this.finishedAtIso = finishedAtIso;
        }

        public Long getDurationMillis() {
            return durationMillis;
        }

        public void setDurationMillis(Long durationMillis) {
            this.durationMillis = durationMillis;
        }

        public String getLastRunAtIso() {
            return lastRunAtIso;
        }

        public void setLastRunAtIso(String lastRunAtIso) {
            this.lastRunAtIso = lastRunAtIso;
        }

        public String getLastRunStatus() {
            return lastRunStatus;
        }

        public void setLastRunStatus(String lastRunStatus) {
            this.lastRunStatus = lastRunStatus;
        }

        public String getLastRunDetail() {
            return lastRunDetail;
        }

        public void setLastRunDetail(String lastRunDetail) {
            this.lastRunDetail = lastRunDetail;
        }

        public Integer getLastRunCount() {
            return lastRunCount;
        }

        public void setLastRunCount(Integer lastRunCount) {
            this.lastRunCount = lastRunCount;
        }

        public String getLastRunUser() {
            return lastRunUser;
        }

        public void setLastRunUser(String lastRunUser) {
            this.lastRunUser = lastRunUser;
        }

        public Integer getPendingCount() {
            return pendingCount;
        }

        public void setPendingCount(Integer pendingCount) {
            this.pendingCount = pendingCount;
        }

        public String getPendingCountUnit() {
            return pendingCountUnit;
        }

        public void setPendingCountUnit(String pendingCountUnit) {
            this.pendingCountUnit = pendingCountUnit;
        }

        public String getPendingCountUpdatedAtIso() {
            return pendingCountUpdatedAtIso;
        }

        public void setPendingCountUpdatedAtIso(String pendingCountUpdatedAtIso) {
            this.pendingCountUpdatedAtIso = pendingCountUpdatedAtIso;
        }
    }

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

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTrigger() {
            return trigger;
        }

        public void setTrigger(String trigger) {
            this.trigger = trigger;
        }

        public String getRunnerUser() {
            return runnerUser;
        }

        public void setRunnerUser(String runnerUser) {
            this.runnerUser = runnerUser;
        }

        public String getStartedAtIso() {
            return startedAtIso;
        }

        public void setStartedAtIso(String startedAtIso) {
            this.startedAtIso = startedAtIso;
        }

        public String getFinishedAtIso() {
            return finishedAtIso;
        }

        public void setFinishedAtIso(String finishedAtIso) {
            this.finishedAtIso = finishedAtIso;
        }

        public Long getDurationMillis() {
            return durationMillis;
        }

        public void setDurationMillis(Long durationMillis) {
            this.durationMillis = durationMillis;
        }

        public Integer getProcessedCount() {
            return processedCount;
        }

        public void setProcessedCount(Integer processedCount) {
            this.processedCount = processedCount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}
