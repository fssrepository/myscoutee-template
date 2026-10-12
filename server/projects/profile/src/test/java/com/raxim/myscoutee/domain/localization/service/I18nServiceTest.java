package com.raxim.myscoutee.domain.localization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.raxim.myscoutee.domain.localization.document.I18nBundle;
import com.raxim.myscoutee.domain.localization.document.I18nMessage;
import com.raxim.myscoutee.domain.localization.dto.I18nBundleResponseDto;
import com.raxim.myscoutee.domain.localization.repository.I18nBundleRepository;
import com.raxim.myscoutee.domain.localization.repository.I18nMessageRepository;

@org.junit.jupiter.api.Tag("standard")
class I18nServiceTest {
    @Test
    void returnsTheMongoBackedEnglishBundle() {
        I18nMessageRepository messageRepository =
                org.mockito.Mockito.mock(I18nMessageRepository.class);
        I18nBundleRepository bundleRepository =
                org.mockito.Mockito.mock(I18nBundleRepository.class);
        I18nBundle bundle = bundle("en", "2026.07.29.en.46");
        I18nMessage title = message(
                "en",
                "add.myscoutee.to.your.home.screen",
                "Add {productName} to your home screen");
        I18nMessage description = message(
                "en",
                "install.prompt.description",
                "Install {productName} for quick access from your home screen.");
        when(bundleRepository.findAll()).thenReturn(List.of(bundle));
        when(messageRepository.findAll()).thenReturn(List.of(title, description));

        I18nService service = new I18nService(messageRepository, bundleRepository);

        I18nBundleResponseDto response =
                service.getBundle("en", "en-US", null);
        I18nBundleResponseDto cachedResponse =
                service.getBundle("en", "en-US", null);

        assertThat(response.lang()).isEqualTo("en");
        assertThat(response.version()).isEqualTo("2026.07.29.en.46");
        assertThat(response.data())
                .containsEntry(
                        "add.myscoutee.to.your.home.screen",
                        "Add {productName} to your home screen");
        assertThat(response.data())
                .containsEntry(
                        "install.prompt.description",
                        "Install {productName} for quick access from your home screen.");
        assertThat(cachedResponse).isEqualTo(response);
        verify(bundleRepository).findAll();
        verify(messageRepository).findAll();
    }

    @Test
    void returnsTheMongoBackedHungarianInstallPromptBundle() {
        I18nMessageRepository messageRepository =
                org.mockito.Mockito.mock(I18nMessageRepository.class);
        I18nBundleRepository bundleRepository =
                org.mockito.Mockito.mock(I18nBundleRepository.class);
        I18nBundle englishBundle = bundle("en", "2026.07.29.en.46");
        I18nBundle hungarianBundle = bundle("hu", "2026.07.29.hu.46");
        I18nMessage title = message(
                "hu",
                "add.myscoutee.to.your.home.screen",
                "{productName} hozzáadása a kezdőképernyőhöz");
        I18nMessage description = message(
                "hu",
                "install.prompt.description",
                "{productName} telepítésével gyorsan elérheted az alkalmazást a kezdőképernyőről.");
        when(bundleRepository.findAll()).thenReturn(List.of(englishBundle, hungarianBundle));
        when(messageRepository.findAll()).thenReturn(List.of(title, description));

        I18nBundleResponseDto response =
                new I18nService(messageRepository, bundleRepository)
                        .getBundle("hu", "hu-HU,en-US", null);

        assertThat(response.lang()).isEqualTo("hu");
        assertThat(response.version()).isEqualTo("2026.07.29.hu.46");
        assertThat(response.data())
                .containsEntry(
                        "add.myscoutee.to.your.home.screen",
                        "{productName} hozzáadása a kezdőképernyőhöz")
                .containsEntry(
                        "install.prompt.description",
                        "{productName} telepítésével gyorsan elérheted az alkalmazást a kezdőképernyőről.");
        verify(messageRepository).findAll();
    }

    @Test
    void returnsPayloadEvenWhenEditorVersionHasNotChanged() {
        I18nMessageRepository messageRepository =
                org.mockito.Mockito.mock(I18nMessageRepository.class);
        I18nBundleRepository bundleRepository =
                org.mockito.Mockito.mock(I18nBundleRepository.class);
        I18nBundle bundle = bundle("en", "2026.07.29.en.46");
        when(bundleRepository.findAll()).thenReturn(List.of(bundle));
        when(messageRepository.findAll()).thenReturn(List.of());

        I18nBundleResponseDto response =
                new I18nService(messageRepository, bundleRepository)
                        .getBundle(
                                "en",
                                "en-US",
                                "2026.07.29.en.46");

        assertThat(response.lang()).isEqualTo("en");
        assertThat(response.version()).isEqualTo("2026.07.29.en.46");
        assertThat(response.data()).isEmpty();
    }

    @Test
    void periodicRefreshReloadsMessagesOnlyWhenTheBundleVersionChanges() {
        I18nMessageRepository messageRepository =
                org.mockito.Mockito.mock(I18nMessageRepository.class);
        I18nBundleRepository bundleRepository =
                org.mockito.Mockito.mock(I18nBundleRepository.class);
        AtomicReference<String> version = new AtomicReference<>("v1");
        AtomicReference<String> value = new AtomicReference<>("Before");
        when(bundleRepository.findAll()).thenAnswer(ignored -> List.of(bundle("en", version.get())));
        when(messageRepository.findAll()).thenAnswer(ignored -> List.of(message("en", "key", value.get())));
        I18nService service = new I18nService(messageRepository, bundleRepository);

        assertThat(service.getBundle("en", null, null).data()).containsEntry("key", "Before");

        service.refreshCache();

        assertThat(service.getBundle("en", null, null).data()).containsEntry("key", "Before");
        verify(messageRepository, times(3)).findAll();

        version.set("v2");
        value.set("After");
        service.refreshCache();

        I18nBundleResponseDto refreshed = service.getBundle("en", null, "v1");
        assertThat(refreshed.version()).isEqualTo("v2");
        assertThat(refreshed.data()).containsEntry("key", "After");
        verify(messageRepository, times(5)).findAll();
    }

    @Test
    void messageEditsAndDeletesRefreshWithoutChangingTheBundleVersion() {
        var messages = org.mockito.Mockito.mock(I18nMessageRepository.class);
        var bundles = org.mockito.Mockito.mock(I18nBundleRepository.class);
        when(bundles.findAll()).thenReturn(List.of(bundle("en", "v1")));
        when(messages.findAll()).thenReturn(List.of(message("en", "key", "Before")));
        var service = new I18nService(messages, bundles);
        assertThat(service.getBundle("en", null, "v1").data()).containsEntry("key", "Before");
        when(messages.findAll()).thenReturn(List.of(message("en", "key", "After")));
        service.refreshCache();
        assertThat(service.getBundle("en", null, "v1").data()).containsEntry("key", "After");
        when(messages.findAll()).thenReturn(List.of());
        service.refreshCache();
        assertThat(service.getBundle("en", null, "v1").data()).doesNotContainKey("key");
    }

    private static I18nBundle bundle(String lang, String version) {
        I18nBundle bundle = new I18nBundle();
        bundle.setLang(lang);
        bundle.setVersion(version);
        return bundle;
    }

    private static I18nMessage message(
            String lang,
            String key,
            String value) {
        I18nMessage message = new I18nMessage();
        message.setLang(lang);
        message.setKey(key);
        message.setValue(value);
        return message;
    }
}
