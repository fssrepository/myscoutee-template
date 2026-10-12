package com.raxim.myscoutee.domain.platform.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.platform.document.CountryPartition;

public interface CountryPartitionsRepository extends MongoRepository<CountryPartition, String> {
    Optional<CountryPartition> findByPartitionKey(String partitionKey);
}
