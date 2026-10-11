package com.raxim.myscoutee.domain.platform.api;
import java.util.Optional;
/** Read-only deployment configuration; updates remain with the deployment owner. */
public interface DeploymentSettings {
    String SINGLETON_ID = "deployment-configuration";
    Optional<? extends Configuration> findById(String id);
    interface Configuration { Integration getIntegration(); }
    interface Integration { String getPublicBaseUrl(); }
}
