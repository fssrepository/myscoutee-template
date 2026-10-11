package com.raxim.myscoutee.domain.localization.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.localization.document.I18nMessage;

public interface I18nMessageRepository extends MongoRepository<I18nMessage, String> {
    List<I18nMessage> findByLang(String lang);
}
