package com.raxim.myscoutee.domain.accounts.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.accounts.document.UserSessionAction;

public interface UserSessionActionsRepository extends MongoRepository<UserSessionAction, String> {
    List<UserSessionAction> findByUserIdOrderByCreatedDateDesc(String userId);
}
