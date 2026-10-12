package com.raxim.myscoutee.domain.platform.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.platform.document.AppSettings;

public interface AppSettingsRepository extends MongoRepository<AppSettings, String> {
    Optional<AppSettings> findBySettingsKey(String settingsKey);
}
