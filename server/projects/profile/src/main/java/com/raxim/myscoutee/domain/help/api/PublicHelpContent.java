package com.raxim.myscoutee.domain.help.api;

import com.raxim.myscoutee.domain.help.dto.HelpCenterDtos.HelpCenterStateDto;

/** Public active document read; no revision administration or consent mutation capability. */
public interface PublicHelpContent {
    HelpCenterStateDto activeState(String documentType, String lang, String acceptLanguage,
            String contextKey);
}
