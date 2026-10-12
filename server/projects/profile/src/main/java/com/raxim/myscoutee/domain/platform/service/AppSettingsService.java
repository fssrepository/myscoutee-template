package com.raxim.myscoutee.domain.platform.service;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;
import com.raxim.myscoutee.domain.platform.document.AppSettings;
import com.raxim.myscoutee.domain.platform.repository.AppSettingsRepository;

import jakarta.annotation.PostConstruct;

@Service
public class AppSettingsService implements com.raxim.myscoutee.domain.platform.api.AdministratorSettings {
    public static final String GLOBAL_SETTINGS_KEY = "global";

    private static final Logger LOGGER = LoggerFactory.getLogger(AppSettingsService.class);

    private final AppSettingsRepository appSettingsRepository;
    private final Clock clock;
    private final EnumMap<MongoSessionMode, AtomicReference<CachedAppSettings>>
            cachedSettings = new EnumMap<>(MongoSessionMode.class);
    private final EnumMap<MongoSessionMode, Object> modeLocks =
            new EnumMap<>(MongoSessionMode.class);

    @Autowired
    public AppSettingsService(AppSettingsRepository appSettingsRepository) {
        this(appSettingsRepository, Clock.systemUTC());
    }

    AppSettingsService(
            AppSettingsRepository appSettingsRepository,
            Clock clock) {
        this.appSettingsRepository = appSettingsRepository;
        this.clock = clock;
        for (MongoSessionMode mode : MongoSessionMode.values()) {
            cachedSettings.put(
                    mode,
                    new AtomicReference<>(CachedAppSettings.empty()));
            modeLocks.put(mode, new Object());
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    void watchCache(com.raxim.myscoutee.common.config.mongo.MongoChangeStreamService changes,
            com.raxim.myscoutee.common.config.mongo.ScheduledMongoSessionRunner databases) {
        if (!databases.hasDistinctDemoDatabase()) {
            cachedSettings.put(MongoSessionMode.DEMO, cachedSettings.get(MongoSessionMode.REAL));
            modeLocks.put(MongoSessionMode.DEMO, modeLocks.get(MongoSessionMode.REAL));
        }
        changes.watch("AppSettingsService", java.util.Set.of("appSettings"), java.util.Set.of(), () -> {
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

    private boolean refreshCache(MongoSessionMode mode) {
        synchronized (modeLocks.get(mode)) {
            try {
                cachedSettings.get(mode).set(inMode(
                        mode,
                        () -> toCachedSettings(loadGlobalSettings())));
                return true;
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "App settings cache refresh failed for {} mode; keeping "
                                + "previous admin access settings.",
                        mode,
                        exception);
                return false;
            }
        }
    }

    public void evictCache() {
        refreshCache(MongoSessionModeContext.currentMode());
    }

    public boolean isAdminEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return false;
        }
        CachedAppSettings settings = currentSettings();
        return settings.adminAccessEnabled() && settings.adminEmails().contains(normalizedEmail);
    }

    public List<String> adminEmails() {
        return currentSettings().adminEmails();
    }

    public List<String> updateAdminEmails(List<String> adminEmails) {
        MongoSessionMode mode = MongoSessionModeContext.currentMode();
        synchronized (modeLocks.get(mode)) {
            List<String> normalizedEmails = normalizeEmails(adminEmails);
            AppSettings settings = loadGlobalSettings();
            if (settings == null) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Application settings seed is missing.");
            }
            settings.setAdminAccessEnabled(true);
            settings.setAdminEmails(normalizedEmails);
            settings.setUpdatedDate(clock.instant().toString());
            AppSettings saved = appSettingsRepository.save(settings);
            CachedAppSettings cached = toCachedSettings(saved);
            cachedSettings.get(mode).set(cached);
            return cached.adminEmails();
        }
    }

    private AppSettings loadGlobalSettings() {
        return appSettingsRepository.findBySettingsKey(GLOBAL_SETTINGS_KEY)
                .orElse(null);
    }

    private CachedAppSettings toCachedSettings(AppSettings settings) {
        if (settings == null) {
            return CachedAppSettings.empty();
        }
        return new CachedAppSettings(
                Boolean.TRUE.equals(settings.getAdminAccessEnabled()),
                normalizeEmails(settings.getAdminEmails()));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeEmails(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String email : values == null ? List.<String>of() : values) {
            String normalizedEmail = normalizeEmail(email);
            if (StringUtils.hasText(normalizedEmail)) {
                normalized.add(normalizedEmail);
            }
        }
        return List.copyOf(normalized);
    }

    private CachedAppSettings currentSettings() {
        return cachedSettings.get(MongoSessionModeContext.currentMode()).get();
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

    private record CachedAppSettings(
            boolean adminAccessEnabled,
            List<String> adminEmails) {
        private static CachedAppSettings empty() {
            return new CachedAppSettings(true, List.of());
        }
    }
}
