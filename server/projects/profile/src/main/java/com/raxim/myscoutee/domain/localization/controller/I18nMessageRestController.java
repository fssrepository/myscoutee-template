package com.raxim.myscoutee.domain.localization.controller;


import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.raxim.myscoutee.domain.localization.dto.I18nBundleResponseDto;
import com.raxim.myscoutee.domain.localization.service.I18nService;

@RestController
@RequestMapping("i18n")
public class I18nMessageRestController {

    private final I18nService i18nService;

    public I18nMessageRestController(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @GetMapping("/bundle")
    public ResponseEntity<I18nBundleResponseDto> getBundle(
            @RequestHeader(name = "accept-language", defaultValue = "en") String lang,
            @RequestParam(name = "lang", required = false) String requestedLang,
            @RequestParam(name = "version", required = false) String version) {
        I18nBundleResponseDto bundle = i18nService.getBundle(requestedLang, lang, version);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(bundle);
    }
}
