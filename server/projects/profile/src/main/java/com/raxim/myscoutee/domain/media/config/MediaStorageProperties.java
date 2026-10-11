package com.raxim.myscoutee.domain.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("media")
@org.springframework.context.annotation.Configuration
public class MediaStorageProperties {
    private boolean enabled = true;
    private String endpoint = "http://localhost:9000";
    private String region = "eu-central-1";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "myscoutee-media";
    private boolean pathStyleAccess = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }
}
