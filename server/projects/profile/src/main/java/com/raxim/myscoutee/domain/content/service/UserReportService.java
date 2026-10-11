package com.raxim.myscoutee.domain.content.service;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.content.document.UserReportSubmission;
import com.raxim.myscoutee.domain.content.repository.UserReportSubmissionsRepository;
@Service
public class UserReportService implements com.raxim.myscoutee.domain.content.api.UserReports {
    private final UserReportSubmissionsRepository reports;
    public UserReportService(UserReportSubmissionsRepository reports) { this.reports = reports; }
    public boolean exists(String syncKey) { return reports.findBySyncKey(syncKey).isPresent(); }
    public Receipt submit(ReportDraft request) {
        var document = new UserReportSubmission();
        document.setId(new org.bson.types.ObjectId().toHexString());
        document.setSyncKey(request.syncKey());
        document.setUserId(request.userId());
        document.setHandle(request.handle());
        document.setReason(request.reason());
        document.setDetails(request.details());
        document.setTargetUserId(request.targetUserId());
        document.setMemberEntryId(request.memberEntryId());
        document.setEventId(request.eventId());
        document.setEventTitle(request.eventTitle());
        document.setEventStartAtIso(request.eventStartAtIso());
        document.setSourceType(request.sourceType());
        document.setSourceId(request.sourceId());
        document.setSourceText(request.sourceText());
        document.setChatId(request.chatId());
        document.setMessageId(request.messageId());
        document.setAssetId(request.assetId());
        document.setAssetType(request.assetType());
        String now = Instant.now().toString();
        document.setCreatedDate(now); document.setUpdatedDate(now);
        document.setCreatedUser(request.userId()); document.setUpdatedUser(request.userId());
        try { reports.save(document); }
        catch (org.springframework.dao.DuplicateKeyException duplicate) { return new Receipt(null, true); }
        return new Receipt(document.getId(), false);
    }
}
