package com.raxim.myscoutee.domain.localization.dto;

import java.util.Map;

public record I18nBundleResponseDto(String lang, String version, Map<String, String> data) {
}
