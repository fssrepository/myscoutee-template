package com.raxim.myscoutee.domain.showcase.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.raxim.myscoutee.domain.showcase.document.SampleCard;

public interface SampleCardRepository extends MongoRepository<SampleCard, String> {}
