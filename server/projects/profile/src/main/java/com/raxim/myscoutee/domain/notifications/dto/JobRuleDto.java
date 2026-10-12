package com.raxim.myscoutee.domain.notifications.dto;

import java.util.List;
import com.raxim.myscoutee.domain.notifications.api.NotificationJobs;

/** Only the enabled flag and parameter text consumed by scheduled processors. */
public record JobRuleDto(Boolean enabled, List<ParameterDto> parameters) implements NotificationJobs.Configuration {
    public JobRuleDto {
        if (parameters != null) parameters = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(parameters));
    }
    public static JobRuleDto from(NotificationJobs.Configuration value) {
        if (value == null) return null;
        return new JobRuleDto(value.getEnabled(), value.getParameters() == null ? null : value.getParameters().stream()
                .map(parameter -> parameter == null ? null : new ParameterDto(parameter.getKey(), parameter.getTextValue())).toList());
    }
    @Override public Boolean getEnabled() { return enabled; }
    @Override public List<ParameterDto> getParameters() { return parameters; }
    public record ParameterDto(String key, String textValue) implements NotificationJobs.Parameter {
        @Override public String getKey() { return key; }
        @Override public String getTextValue() { return textValue; }
    }
}
