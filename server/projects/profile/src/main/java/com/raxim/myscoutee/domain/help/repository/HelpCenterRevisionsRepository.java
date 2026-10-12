package com.raxim.myscoutee.domain.help.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.help.document.HelpCenterRevision;

public interface HelpCenterRevisionsRepository extends MongoRepository<HelpCenterRevision, String> {
    Optional<HelpCenterRevision> findFirstByBaseGroupIdAndDocumentTypeAndLangAndContextKeyAndActiveTrueOrderByVersionDesc(
            String baseGroupId, String documentType, String lang, String contextKey);

    boolean existsByDocumentTypeAndContextKeyAndIsSystemTrue(String documentType, String contextKey);

    Optional<HelpCenterRevision> findFirstByActiveTrueOrderByVersionDesc();

    List<HelpCenterRevision> findAllByOrderByVersionDesc();

    List<HelpCenterRevision> findByBaseGroupIdOrderByVersionDesc(String baseGroupId);

    Optional<HelpCenterRevision> findFirstByDocumentTypeAndLangAndActiveTrueOrderByVersionDesc(String documentType, String lang);

    Optional<HelpCenterRevision> findFirstByOrderByVersionDesc();
}
