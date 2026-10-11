package com.raxim.myscoutee.domain.content.service;
import java.time.Instant;
import java.util.UUID;
import org.bson.types.ObjectId;
import com.raxim.myscoutee.domain.content.document.UserFeedbackSubmission;
import com.raxim.myscoutee.domain.content.repository.UserFeedbackSubmissionsRepository;
@org.springframework.stereotype.Service
public class UserFeedbackService implements com.raxim.myscoutee.domain.content.api.UserFeedback {
    private final UserFeedbackSubmissionsRepository userFeedbackSubmissionsRepository;
    public UserFeedbackService(UserFeedbackSubmissionsRepository repository) { userFeedbackSubmissionsRepository = repository; }
    public String submit(String userId, String category, String subject, String details) {
        var document = document(new ObjectId().toHexString(), userId, category, subject, details);
        userFeedbackSubmissionsRepository.save(document);
        return document.getId();
    }
    @Override public String submitOnce(String requestId, String userId, String category, String subject, String details) {
        if (requestId == null || !requestId.matches("[a-f0-9]{24}")) throw new IllegalArgumentException("Invalid feedback request ID");
        var document = document(requestId, userId, category, subject, details);
        try { userFeedbackSubmissionsRepository.insert(document); }
        catch (org.springframework.dao.DuplicateKeyException duplicate) {
            var saved = userFeedbackSubmissionsRepository.findById(requestId).orElseThrow(() -> duplicate);
            if (!java.util.Objects.equals(saved.getUserId(), userId)
                    || !java.util.Objects.equals(saved.getCategory(), category)
                    || !java.util.Objects.equals(saved.getSubject(), subject)
                    || !java.util.Objects.equals(saved.getDetails(), details))
                throw new IllegalArgumentException("Feedback request ID already belongs to another submission");
        }
        return requestId;
    }
    private UserFeedbackSubmission document(String id, String userId, String category, String subject, String details) {
        String nowIso = Instant.now().toString();
        UserFeedbackSubmission document = new UserFeedbackSubmission();
        document.setId(id);
        document.setSyncKey(UUID.randomUUID().toString());
        document.setUserId(userId);
        document.setCategory(category);
        document.setSubject(subject);
        document.setDetails(details);
        document.setCreatedDate(nowIso);
        document.setCreatedUser(userId);
        document.setUpdatedDate(nowIso);
        document.setUpdatedUser(userId);
        return document;
    }
}
