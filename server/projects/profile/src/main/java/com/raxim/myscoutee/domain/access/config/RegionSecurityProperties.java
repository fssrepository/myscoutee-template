package com.raxim.myscoutee.domain.access.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.region-security")
public class RegionSecurityProperties implements com.raxim.myscoutee.domain.access.api.RegionSecurity {
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
