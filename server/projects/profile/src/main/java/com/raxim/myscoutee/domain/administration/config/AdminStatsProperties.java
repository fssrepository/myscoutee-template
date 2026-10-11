package com.raxim.myscoutee.domain.administration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("admin.stats")
public class AdminStatsProperties {
    private boolean enabled = true;
    private String streamKey = "admin:telemetry";
    private int maxBatchSize = 500;
    private int streamRetentionDays = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey == null || streamKey.isBlank() ? "admin:telemetry" : streamKey.trim();
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = Math.max(1, maxBatchSize);
    }

    public int getStreamRetentionDays() {
        return streamRetentionDays;
    }

    public void setStreamRetentionDays(int streamRetentionDays) {
        this.streamRetentionDays = Math.max(1, streamRetentionDays);
    }
}
