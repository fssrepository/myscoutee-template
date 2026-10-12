package com.raxim.myscoutee.domain.platform.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "appSettings")
public class AppSettings {
    @Id
    private String id;

    @Indexed(name = "app_settings_key", unique = true)
    private String settingsKey;

    private Boolean adminAccessEnabled = true;
    private List<String> adminEmails = new ArrayList<>();
    private String createdDate;
    private String updatedDate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSettingsKey() {
        return settingsKey;
    }

    public void setSettingsKey(String settingsKey) {
        this.settingsKey = settingsKey;
    }

    public Boolean getAdminAccessEnabled() {
        return adminAccessEnabled;
    }

    public void setAdminAccessEnabled(Boolean adminAccessEnabled) {
        this.adminAccessEnabled = adminAccessEnabled;
    }

    public List<String> getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(List<String> adminEmails) {
        this.adminEmails = adminEmails;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(String updatedDate) {
        this.updatedDate = updatedDate;
    }
}
