package com.raxim.myscoutee.microservices.transport.localization;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.raxim.myscoutee.domain.localization.api.TranslationBundles;
import com.raxim.myscoutee.domain.localization.dto.I18nBundleResponseDto;
import com.raxim.myscoutee.microservices.transport.common.RemoteReadTransport;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

public final class RemoteTranslationBundles extends RemoteReadTransport implements TranslationBundles {
    public RemoteTranslationBundles(URI origin, String token, boolean allowHttp, Duration connectTimeout,
            Duration requestTimeout, ObjectMapper mapper, MeterRegistry metrics) {
        super(origin, token, allowHttp, connectTimeout, requestTimeout, mapper, metrics);
    }
    @Override public I18nBundleResponseDto getBundle(String requestedLang, String acceptLanguage, String currentVersion) {
        var result = exchange(LocalizationProtocol.PATH + "?lang=" + encode(requestedLang)
                + "&acceptLanguage=" + encode(acceptLanguage) + "&version=" + encode(currentVersion),
                new TypeReference<I18nBundleResponseDto>() {}, "localization.bundle", LocalizationProtocol.MAX_RESPONSE_BYTES, value -> value.lang() != null && value.version() != null);
        return result;
    }
    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
