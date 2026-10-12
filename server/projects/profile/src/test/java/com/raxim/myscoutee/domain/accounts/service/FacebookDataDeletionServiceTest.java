package com.raxim.myscoutee.domain.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.email.config.EmailProperties;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.FacebookDataDeletionResponseDto;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserSubmitActionResponseDto;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

import tools.jackson.databind.ObjectMapper;

@org.junit.jupiter.api.Tag("standard")
class FacebookDataDeletionServiceTest {
    @Test
    void verifiesSignedRequestAndSchedulesMatchingFacebookUserForDeletion() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AccountLifecycleService lifecycle = mock(AccountLifecycleService.class);
        Users user = new Users();
        user.setUserId("user-1");
        user.setFacebookId("facebook-user-1");
        when(usersRepository.findFirstByFacebookId("facebook-user-1")).thenReturn(Optional.of(user));
        when(lifecycle.scheduleUserDeletionByUserId("user-1", "facebook-data-deletion"))
                .thenReturn(new UserSubmitActionResponseDto(true, null));

        FacebookDataDeletionService service = new FacebookDataDeletionService(
                configProperties("facebook-secret"),
                emailProperties("https://myscoutee.com/"),
                usersRepository,
                lifecycle,
                new ObjectMapper());

        FacebookDataDeletionResponseDto response = service.handleSignedRequest(
                signedRequest("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"facebook-user-1\"}", "facebook-secret"));

        assertThat(response.confirmation_code()).startsWith("FB").hasSize(26);
        assertThat(response.url()).isEqualTo("https://myscoutee.com/api/auth/facebook/data-deletion/status?code=" + response.confirmation_code());
        verify(lifecycle).scheduleUserDeletionByUserId("user-1", "facebook-data-deletion");
    }

    @Test
    void acceptsSignedRequestWhenNoLocalUserExists() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        AccountLifecycleService lifecycle = mock(AccountLifecycleService.class);
        when(usersRepository.findFirstByFacebookId("facebook-user-1")).thenReturn(Optional.empty());

        FacebookDataDeletionService service = new FacebookDataDeletionService(
                configProperties("facebook-secret"),
                emailProperties("https://myscoutee.com"),
                usersRepository,
                lifecycle,
                new ObjectMapper());

        FacebookDataDeletionResponseDto response = service.handleSignedRequest(
                signedRequest("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"facebook-user-1\"}", "facebook-secret"));

        assertThat(response.confirmation_code()).startsWith("FB");
        verify(lifecycle, never()).scheduleUserDeletionByUserId("user-1", "facebook-data-deletion");
    }

    @Test
    void rejectsBadSignature() {
        FacebookDataDeletionService service = new FacebookDataDeletionService(
                configProperties("facebook-secret"),
                emailProperties("https://myscoutee.com"),
                mock(UsersRepository.class),
                mock(AccountLifecycleService.class),
                new ObjectMapper());
        String signedRequest = signedRequest("{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"facebook-user-1\"}", "wrong-secret");

        ResponseStatusException exception = catchThrowableOfType(
                () -> service.handleSignedRequest(signedRequest),
                ResponseStatusException.class);

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingAppSecret() {
        FacebookDataDeletionService service = new FacebookDataDeletionService(
                configProperties(""),
                emailProperties("https://myscoutee.com"),
                mock(UsersRepository.class),
                mock(AccountLifecycleService.class),
                new ObjectMapper());

        ResponseStatusException exception = catchThrowableOfType(
                () -> service.handleSignedRequest("signature.payload"),
                ResponseStatusException.class);

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static ConfigProperties configProperties(String appSecret) {
        ConfigProperties configProperties = new ConfigProperties();
        configProperties.setFacebookAppSecret(appSecret);
        return configProperties;
    }

    private static EmailProperties emailProperties(String baseUrl) {
        EmailProperties emailProperties = new EmailProperties();
        emailProperties.setBaseUrl(baseUrl);
        return emailProperties;
    }

    private static String signedRequest(String payloadJson, String secret) {
        String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        return base64Url(hmacSha256(payload, secret)) + "." + payload;
    }

    private static byte[] hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
