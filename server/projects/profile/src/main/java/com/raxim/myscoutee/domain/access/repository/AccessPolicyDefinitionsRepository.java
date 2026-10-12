package com.raxim.myscoutee.domain.access.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.access.document.AccessPolicyDefinition;

public interface AccessPolicyDefinitionsRepository extends MongoRepository<AccessPolicyDefinition, String> {
}
