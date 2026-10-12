package com.raxim.myscoutee.domain.localization.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.localization.document.I18nBundle;
import com.raxim.myscoutee.domain.localization.document.I18nMessage;
import com.raxim.myscoutee.domain.localization.dto.I18nBundleResponseDto;
import com.raxim.myscoutee.domain.localization.repository.I18nBundleRepository;
import com.raxim.myscoutee.domain.localization.repository.I18nMessageRepository;

import jakarta.annotation.PostConstruct;

@Service
public class I18nService implements com.raxim.myscoutee.domain.localization.api.TranslationBundles {
    private static final String DEFAULT_LANG = "en";
    private static final Logger LOGGER = LoggerFactory.getLogger(I18nService.class);

    private final I18nMessageRepository i18nMessageRepository;
    private final I18nBundleRepository i18nBundleRepository;
    private final EnumMap<MongoSessionMode, AtomicReference<CachedBundles>> cachedBundles =
            new EnumMap<>(MongoSessionMode.class);
    private final EnumMap<MongoSessionMode, Object> modeLocks =
            new EnumMap<>(MongoSessionMode.class);

    public I18nService(I18nMessageRepository i18nMessageRepository, I18nBundleRepository i18nBundleRepository) {
        this.i18nMessageRepository = i18nMessageRepository;
        this.i18nBundleRepository = i18nBundleRepository;
        for (MongoSessionMode mode : MongoSessionMode.values()) {
            cachedBundles.put(mode, new AtomicReference<>(CachedBundles.unloaded()));
            modeLocks.put(mode, new Object());
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    void watchCache(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes,
            com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner databases) {
        if (!databases.hasDistinctDemoDatabase()) {
            cachedBundles.put(MongoSessionMode.DEMO, cachedBundles.get(MongoSessionMode.REAL));
            modeLocks.put(MongoSessionMode.DEMO, modeLocks.get(MongoSessionMode.REAL));
        }
        changes.watch("I18nService", java.util.Set.of("i18n_bundles", "i18n_messages"), java.util.Set.of(), () -> {
            MongoSessionMode mode = MongoSessionModeContext.currentMode();
            if (!refreshCache(mode)) return java.time.Instant.now().plusSeconds(5);
            return null;
        });
    }

    @PostConstruct
    public void preloadCache() {
        refreshCache();
    }

    public void refreshCache() {
        for (MongoSessionMode mode : MongoSessionMode.values()) {
            refreshCache(mode);
        }
    }

    public I18nBundleResponseDto getBundle(String requestedLang, String acceptLanguage, String currentVersion) {
        CachedBundles snapshot = currentBundles();
        String selectedLang = selectLanguage(requestedLang, acceptLanguage, snapshot.languages());
        CachedBundle bundle = snapshot.byLanguage().get(selectedLang);
        if (bundle == null) {
            return new I18nBundleResponseDto(DEFAULT_LANG, "0", null);
        }

        String latestVersion = bundle.version();

        // Bundle versions are editor metadata, not content validators: a message can be
        // edited or deleted independently. Return the cached payload even for an equal
        // version so existing clients cannot retain an obsolete translation indefinitely.
        return new I18nBundleResponseDto(selectedLang, latestVersion, bundle.messages());
    }

    private String selectLanguage(
            String requestedLang,
            String acceptLanguage,
            Set<String> availableLangs) {
        if (availableLangs.isEmpty()) {
            return DEFAULT_LANG;
        }

        for (String candidate : languageCandidates(requestedLang, acceptLanguage)) {
            if (DEFAULT_LANG.equals(candidate)) {
                return DEFAULT_LANG;
            }
            if (availableLangs.contains(candidate)) {
                return candidate;
            }
        }
        return DEFAULT_LANG;
    }

    private CachedBundles currentBundles() {
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        CachedBundles current = cachedBundles.get(mode).get();
        if (current.loaded()) {
            return current;
        }
        refreshCache(mode);
        return cachedBundles.get(mode).get();
    }

    private boolean refreshCache(MongoSessionMode mode) {
        synchronized (modeLocks.get(mode)) {
            try {
                CachedBundles current = cachedBundles.get(mode).get();
                CachedBundles refreshed = inMode(mode, () -> loadBundles(current));
                cachedBundles.get(mode).set(refreshed);
                return true;
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "I18n bundle cache refresh failed for {} mode; keeping the previous snapshot.",
                        mode,
                        exception);
                return false;
            }
        }
    }

    private CachedBundles loadBundles(CachedBundles current) {
        Map<String, String> versions = new HashMap<>();
        for (I18nBundle bundle : i18nBundleRepository.findAll()) {
            String lang = normalizeLang(bundle.getLang());
            if (!lang.isBlank()) {
                versions.put(lang, safeText(bundle.getVersion(), "0"));
            }
        }
        Map<String, String> immutableVersions = Map.copyOf(versions);
        Map<String, Map<String, String>> messagesByLanguage = new HashMap<>();
        for (I18nMessage message : i18nMessageRepository.findAll()) {
            String lang = normalizeLang(message.getLang());
            String key = safeText(message.getKey(), "");
            if (lang.isBlank() || key.isBlank() || !versions.containsKey(lang)) {
                continue;
            }
            messagesByLanguage
                    .computeIfAbsent(lang, ignored -> new HashMap<>())
                    .putIfAbsent(key, Objects.toString(message.getValue(), ""));
        }

        Map<String, CachedBundle> byLanguage = new HashMap<>();
        versions.forEach((lang, version) -> byLanguage.put(
                lang,
                new CachedBundle(
                        version,
                        Map.copyOf(messagesByLanguage.getOrDefault(lang, Map.of())))));
        return new CachedBundles(true, immutableVersions, Map.copyOf(byLanguage));
    }

    private <T> T inMode(
            MongoSessionMode mode,
            java.util.function.Supplier<T> action) {
        MongoSessionMode previous = MongoSessionModeContext.currentMode();
        MongoSessionModeContext.set(mode);
        try {
            return action.get();
        } finally {
            MongoSessionModeContext.set(previous);
        }
    }

    private List<String> languageCandidates(String requestedLang, String acceptLanguage) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String explicitLang = normalizeLang(requestedLang);
        if (!explicitLang.isBlank()) {
            candidates.add(explicitLang);
        }
        parseAcceptLanguage(acceptLanguage).stream()
                .map(LanguagePreference::lang)
                .forEach(candidates::add);
        candidates.add(DEFAULT_LANG);
        return new ArrayList<>(candidates);
    }

    private List<LanguagePreference> parseAcceptLanguage(String acceptLanguage) {
        String raw = safeText(acceptLanguage, "");
        if (raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<LanguagePreference> preferences = new ArrayList<>();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index].trim();
            if (part.isBlank()) {
                continue;
            }
            String[] tokens = part.split(";");
            String lang = normalizeLang(tokens[0]);
            if (lang.isBlank()) {
                continue;
            }
            double quality = 1.0d;
            for (int tokenIndex = 1; tokenIndex < tokens.length; tokenIndex++) {
                String token = tokens[tokenIndex].trim();
                if (!token.startsWith("q=")) {
                    continue;
                }
                try {
                    quality = Double.parseDouble(token.substring(2));
                } catch (NumberFormatException ignored) {
                    quality = 1.0d;
                }
            }
            preferences.add(new LanguagePreference(lang, quality, index));
        }
        preferences.sort(Comparator
                .comparingDouble(LanguagePreference::quality)
                .reversed()
                .thenComparingInt(LanguagePreference::index));
        return preferences;
    }

    private String normalizeLang(String value) {
        String raw = safeText(value, "").toLowerCase();
        int commaIndex = raw.indexOf(',');
        if (commaIndex >= 0) {
            raw = raw.substring(0, commaIndex);
        }
        int semicolonIndex = raw.indexOf(';');
        if (semicolonIndex >= 0) {
            raw = raw.substring(0, semicolonIndex);
        }
        int dashIndex = raw.indexOf('-');
        if (dashIndex >= 0) {
            raw = raw.substring(0, dashIndex);
        }
        return raw.chars()
                .filter(ch -> ch >= 'a' && ch <= 'z')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private String safeText(String value, String fallback) {
        String text = Objects.toString(value, "").trim();
        return text.isBlank() ? fallback : text;
    }

    private record LanguagePreference(String lang, double quality, int index) {
    }

    private record CachedBundle(String version, Map<String, String> messages) {
    }

    private record CachedBundles(
            boolean loaded,
            Map<String, String> versions,
            Map<String, CachedBundle> byLanguage) {
        private static CachedBundles unloaded() {
            return new CachedBundles(false, Map.of(), Map.of());
        }

        private Set<String> languages() {
            return byLanguage.keySet();
        }
    }
}
