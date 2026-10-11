package com.raxim.myscoutee.domain.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("email")
public class EmailProperties implements com.raxim.myscoutee.domain.email.api.EmailLinks {
    private boolean enabled = false;
    private String provider = "noop";
    private String fromEmail = "no-reply@myscoutee.local";
    private String fromName = "MyScoutee";
    private String replyTo = "support@myscoutee.local";
    private String baseUrl = "http://localhost:4200";
    private Sendgrid sendgrid = new Sendgrid();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Sendgrid getSendgrid() {
        return sendgrid;
    }

    public void setSendgrid(Sendgrid sendgrid) {
        this.sendgrid = sendgrid == null ? new Sendgrid() : sendgrid;
    }

    public static class Sendgrid {
        private String apiKey = "";
        private String endpoint = "https://api.sendgrid.com/v3/mail/send";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
