package com.raxim.myscoutee.domain.help.api;

import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterAuditEntryDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterGuideFieldDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterRevisionDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterSectionDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterStateDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.PrivacyConsentDto;
import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.PrivacyConsentSaveRequestDto;

public interface HelpCenter extends PublicHelpContent {
    HelpCenterStateDto activeState();
    HelpCenterStateDto activeState(String documentType);
    HelpCenterStateDto activeState(String documentType, String lang);
    HelpCenterStateDto activeState(String documentType, String lang, String acceptLanguage);
    HelpCenterStateDto activeState(String documentType, String lang, String acceptLanguage, String contextKey);
    PrivacyConsentDto privacyConsent(String userId, String revisionId, Integer revisionVersion);
    PrivacyConsentDto savePrivacyConsent(PrivacyConsentSaveRequestDto request);
}
