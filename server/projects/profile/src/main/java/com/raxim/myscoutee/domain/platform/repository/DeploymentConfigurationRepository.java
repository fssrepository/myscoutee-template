package com.raxim.myscoutee.domain.platform.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.platform.document.DeploymentConfiguration;

public interface DeploymentConfigurationRepository
        extends MongoRepository<DeploymentConfiguration, String> {
}
