package com.raxim.myscoutee.domain.platform.security.bootstrap;
import com.raxim.myscoutee.domain.platform.security.bootstrap.OperatorBootstrapTokenInvalidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import com.raxim.myscoutee.domain.accounts.security.firebase.FirebaseService;
import com.raxim.myscoutee.domain.accounts.security.firebase.dto.FirebasePrincipal;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import com.raxim.myscoutee.domain.accounts.document.Users;

@org.junit.jupiter.api.Tag("standard")
class OperatorBootstrapAuthenticationServiceTest {
    private static final String EMAIL =
            "operator-0123456789abcdef01234567@deployment.invalid";
    private static final String PASSWORD =
            "install-random-password-with-high-entropy";
    private static final Instant NOW =
            Instant.parse("2026-07-29T09:00:00Z");

    @Test
    void authenticatesPersistsAndIssuesAnEightHourSignedToken() {
        Fixture fixture = fixture();

        OperatorBootstrapAuthenticationService.LoginResult result =
                fixture.service().login(
                        " " + EMAIL.toUpperCase() + " ",
                        PASSWORD);

        assertThat(result.tokenType()).isEqualTo("OperatorBootstrap");
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.expiresAt())
                .isEqualTo(NOW.plus(Duration.ofHours(8)));
        assertThat(result.accessToken()).isNotBlank()
                .doesNotContain(PASSWORD);
        assertThat(fixture.service().authenticateToken(
                result.accessToken()))
                .isSameAs(fixture.principal());
        verify(fixture.firebaseService())
                .loadOrCreateBootstrapOperator(EMAIL);
        verify(fixture.firebaseService())
                .loadStoredBootstrapOperator(EMAIL);
    }

    @Test
    void disabledOrNonMatchingBootstrapIsNotApplicable() {
        ConfigProperties disabled = new ConfigProperties();
        disabled.setOperatorUser(EMAIL);
        disabled.setOperatorBootstrapEmail(EMAIL);
        FirebaseService firebaseService = mock(FirebaseService.class);
        OperatorBootstrapAuthenticationService disabledService =
                new OperatorBootstrapAuthenticationService(
                        disabled,
                        firebaseService,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        new SecureRandom());

        assertStatus(
                () -> disabledService.login(EMAIL, PASSWORD),
                HttpStatus.NOT_FOUND);

        Fixture fixture = fixture();
        assertStatus(
                () -> fixture.service().login(
                        "another@deployment.invalid",
                        PASSWORD),
                HttpStatus.NOT_FOUND);
        verify(firebaseService, never())
                .loadOrCreateBootstrapOperator(
                        org.mockito.ArgumentMatchers.anyString());

        fixture.properties().setOperatorUser(
                EMAIL + ",second@deployment.invalid");
        assertStatus(
                () -> fixture.service().login(EMAIL, PASSWORD),
                HttpStatus.NOT_FOUND);

        fixture.properties().setOperatorUser(EMAIL.toUpperCase());
        assertStatus(
                () -> fixture.service().login(EMAIL, PASSWORD),
                HttpStatus.NOT_FOUND);

        fixture.properties().setOperatorUser(EMAIL);
        fixture.properties().setOperatorBootstrapEmail(
                "owner@example.invalid");
        assertStatus(
                () -> fixture.service().login(
                        "owner@example.invalid",
                        PASSWORD),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void matchingWrongPasswordIsThrottledWithinABoundedWindow() {
        Fixture fixture = fixture();

        for (int attempt = 0;
                attempt
                        < OperatorBootstrapAuthenticationService
                                .MAX_FAILED_ATTEMPTS;
                attempt++) {
            assertStatus(
                    () -> fixture.service().login(
                            EMAIL,
                            "wrong-password"),
                    HttpStatus.UNAUTHORIZED);
        }
        assertStatus(
                () -> fixture.service().login(EMAIL, PASSWORD),
                HttpStatus.TOO_MANY_REQUESTS);
        verify(fixture.firebaseService(), never())
                .loadOrCreateBootstrapOperator(EMAIL);

        fixture.clock().advance(
                OperatorBootstrapAuthenticationService.FAILURE_WINDOW
                        .plusSeconds(1));
        assertThat(fixture.service().login(EMAIL, PASSWORD)
                .accessToken()).isNotBlank();
    }

    @Test
    void rejectsTamperedExpiredOrConfigurationBoundTokens() {
        Fixture fixture = fixture();
        String token = fixture.service().login(
                EMAIL,
                PASSWORD).accessToken();

        assertThatThrownBy(() -> fixture.service().authenticateToken(
                tamperSignature(token)))
                .isInstanceOf(
                        OperatorBootstrapTokenInvalidException.class);

        fixture.clock().advance(
                OperatorBootstrapAuthenticationService.TOKEN_TTL);
        assertThatThrownBy(() -> fixture.service().authenticateToken(token))
                .isInstanceOf(
                        OperatorBootstrapTokenInvalidException.class);

        Fixture rotated = fixture();
        String issuedBeforeRotation = rotated.service().login(
                EMAIL,
                PASSWORD).accessToken();
        rotated.properties().setOperatorBootstrapPasswordSha256(
                sha256Hex("different-install-password"));
        assertThatThrownBy(() -> rotated.service().authenticateToken(
                issuedBeforeRotation))
                .isInstanceOf(
                        OperatorBootstrapTokenInvalidException.class);

        Fixture rebound = fixture();
        String issuedForInstalledSubject = rebound.service().login(
                EMAIL,
                PASSWORD).accessToken();
        rebound.properties().setOperatorUser(
                "replacement@deployment.invalid");
        assertStatus(
                () -> rebound.service().login(
                        "replacement@deployment.invalid",
                        PASSWORD),
                HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> rebound.service().authenticateToken(
                issuedForInstalledSubject))
                .isInstanceOf(
                        OperatorBootstrapTokenInvalidException.class);
    }

    @Test
    void rejectsOversizedPasswordsAndPrincipalsWithoutOperatorAuthority() {
        Fixture fixture = fixture();
        assertStatus(
                () -> fixture.service().login(
                        EMAIL,
                        "x".repeat(
                                OperatorBootstrapAuthenticationService
                                        .PASSWORD_MAX_LENGTH
                                        + 1)),
                HttpStatus.UNAUTHORIZED);

        UserDetails memberPrincipal = principal(List.of("U"));
        when(fixture.firebaseService()
                .loadOrCreateBootstrapOperator(EMAIL))
                .thenReturn(memberPrincipal);
        assertStatus(
                () -> fixture.service().login(EMAIL, PASSWORD),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    private Fixture fixture() {
        ConfigProperties properties = new ConfigProperties();
        properties.setOperatorUser(EMAIL);
        properties.setOperatorBootstrapEmail(EMAIL);
        properties.setOperatorBootstrapPasswordSha256(
                sha256Hex(PASSWORD));
        FirebaseService firebaseService = mock(FirebaseService.class);
        UserDetails principal = principal(List.of("U", "O"));
        when(firebaseService.loadOrCreateBootstrapOperator(EMAIL))
                .thenReturn(principal);
        when(firebaseService.loadStoredBootstrapOperator(EMAIL))
                .thenReturn(principal);
        MutableClock clock = new MutableClock(NOW);
        return new Fixture(
                properties,
                firebaseService,
                principal,
                clock,
                new OperatorBootstrapAuthenticationService(
                        properties,
                        firebaseService,
                        clock,
                        new SecureRandom()));
    }

    private UserDetails principal(List<String> authorities) {
        Users user = new Users();
        user.setUserId("operator-bootstrap");
        user.setEmail(EMAIL);
        user.setOperator(authorities.contains("O"));
        return new FirebasePrincipal(
                "operator-bootstrap",
                EMAIL,
                authorities,
                user);
    }

    private String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char original = token.charAt(signatureStart);
        char replacement = original == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart)
                + replacement
                + token.substring(signatureStart + 1);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertStatus(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            HttpStatus expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(
                                exception.getStatusCode())
                                .isEqualTo(expected));
    }

    private record Fixture(
            ConfigProperties properties,
            FirebaseService firebaseService,
            UserDetails principal,
            MutableClock clock,
            OperatorBootstrapAuthenticationService service) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
