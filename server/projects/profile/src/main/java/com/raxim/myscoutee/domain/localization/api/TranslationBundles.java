package com.raxim.myscoutee.domain.localization.api;

import com.raxim.myscoutee.domain.localization.dto.I18nBundleResponseDto;

public interface TranslationBundles {
    I18nBundleResponseDto getBundle(String requestedLang, String acceptLanguage, String currentVersion);
}
