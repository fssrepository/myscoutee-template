package com.raxim.myscoutee.domain.access.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.access.document.AccessGrant;

public interface AccessGrantsRepository extends MongoRepository<AccessGrant, String> {
}
