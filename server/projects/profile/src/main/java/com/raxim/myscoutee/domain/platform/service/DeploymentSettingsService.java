package com.raxim.myscoutee.domain.platform.service;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.platform.api.DeploymentSettings;
import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;
import com.raxim.myscoutee.domain.platform.repository.DeploymentConfigurationRepository;
@Service
public class DeploymentSettingsService implements DeploymentSettings {
    private final DeploymentConfigurationRepository repository;
    public DeploymentSettingsService(DeploymentConfigurationRepository repository) { this.repository = repository; }
    @Override public java.util.Optional<DeploymentConfiguration> findById(String id) { return repository.findById(id); }
}
