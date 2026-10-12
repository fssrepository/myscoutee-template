package com.raxim.myscoutee.domain.platform.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "operator.configuration")
public class OperatorConfigurationProperties {
    private Path masterKeyPath =
            Path.of("/etc/myscoutee/configuration/deployment-master-key");

    public Path getMasterKeyPath() {
        return masterKeyPath;
    }

    public void setMasterKeyPath(Path masterKeyPath) {
        this.masterKeyPath = masterKeyPath;
    }
}
