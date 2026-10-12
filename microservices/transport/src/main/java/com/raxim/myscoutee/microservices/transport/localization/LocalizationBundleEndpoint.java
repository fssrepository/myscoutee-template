package com.raxim.myscoutee.microservices.transport.localization;

import com.raxim.myscoutee.domain.localization.api.TranslationBundles;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class LocalizationBundleEndpoint {
    private final TranslationBundles bundles;
    public LocalizationBundleEndpoint(TranslationBundles bundles) { this.bundles = bundles; }

    @GetMapping(value = LocalizationProtocol.PATH, produces = "application/json")
    ResponseEntity<?> bundle(@RequestParam(required = false) String lang,
            @RequestParam(required = false) String acceptLanguage,
            @RequestParam(required = false) String version) {
        if (tooLong(lang, 32) || tooLong(acceptLanguage, 512) || tooLong(version, 128)) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                    .body(new LocalizationProtocol.Error("INVALID_REQUEST"));
        }
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(bundles.getBundle(lang, acceptLanguage, version));
        } catch (RuntimeException failure) {
            // Never expose persistence messages or data in the wire error.
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                    .body(new LocalizationProtocol.Error("LOCALIZATION_UNAVAILABLE"));
        }
    }
    private boolean tooLong(String value, int limit) { return value != null && value.length() > limit; }
}
