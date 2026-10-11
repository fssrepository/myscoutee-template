package com.raxim.myscoutee.domain.content.api;
public interface UserReports {
    boolean exists(String syncKey);
    Receipt submit(ReportDraft request);
    record Receipt(String id, boolean duplicate) {}
    record ReportDraft(String syncKey, String userId, String handle, String reason, String details, String targetUserId, String memberEntryId, String eventId, String eventTitle, String eventStartAtIso, String sourceType, String sourceId, String sourceText, String chatId, String messageId, String assetId, String assetType) {}
}
