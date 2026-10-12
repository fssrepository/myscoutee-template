package com.raxim.myscoutee.domain.platform.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("operator.tls")
public class OperatorTlsProperties {
    private boolean enabled;
    private Path stateDir = Path.of("/var/lib/myscoutee/tls");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getStateDir() {
        return stateDir;
    }

    public void setStateDir(Path stateDir) {
        if (stateDir != null) {
            this.stateDir = stateDir;
        }
    }
}
