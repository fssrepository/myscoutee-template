package com.raxim.myscoutee.domain.content.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.raxim.myscoutee.domain.content.document.UserFeedbackSubmission;

public interface UserFeedbackSubmissionsRepository extends MongoRepository<UserFeedbackSubmission, String> {
    List<UserFeedbackSubmission> findByUserIdOrderByCreatedDateDesc(String userId);

    List<UserFeedbackSubmission> findAllByOrderByCreatedDateDesc();
}
