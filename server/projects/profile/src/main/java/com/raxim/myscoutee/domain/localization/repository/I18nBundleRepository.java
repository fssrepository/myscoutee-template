package com.raxim.myscoutee.domain.localization.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.localization.document.I18nBundle;

public interface I18nBundleRepository extends MongoRepository<I18nBundle, String> {
    Optional<I18nBundle> findByLang(String lang);
}
