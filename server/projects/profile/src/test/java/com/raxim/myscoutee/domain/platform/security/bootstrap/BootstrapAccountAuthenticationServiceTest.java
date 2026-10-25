package com.raxim.myscoutee.domain.platform.security.bootstrap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.raxim.myscoutee.domain.accounts.api.BootstrapAccounts;
import com.raxim.myscoutee.domain.platform.config.ConfigProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.server.ResponseStatusException;
@Tag("standard")
class BootstrapAccountAuthenticationServiceTest {
    static final String EMAIL = "operator-0123456789abcdef01234567@deployment.invalid";
    ConfigProperties config = new ConfigProperties();
    BootstrapAccounts accounts = mock(BootstrapAccounts.class);
    @BeforeEach void configure() throws Exception {
        config.setOperatorUser(EMAIL); config.setOperatorBootstrapEmail(EMAIL);
        config.setOperatorBootstrapPasswordSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("secret".getBytes(StandardCharsets.UTF_8))));
        when(accounts.loadOrCreateBootstrapOperator(EMAIL)).thenReturn(User.withUsername(EMAIL).password("").authorities("U", "O").build());
    }
    @Test void returnsTheProvisionedAccountWithoutIssuingASpecialSession() {
        var auth = new BootstrapAccountAuthenticationService(config, accounts);
        assertThat(auth.authenticate(EMAIL, "secret").getUsername()).isEqualTo(EMAIL);
        verify(accounts).loadOrCreateBootstrapOperator(EMAIL);
    }
    @Test void unrelatedEmailDoesNotSelectTheProvisionedAccount() {
        assertThatThrownBy(() -> new BootstrapAccountAuthenticationService(config, accounts).authenticate("other@example.test", "secret"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(accounts);
    }
    @Test void rejectedPasswordsAreThrottledAndWindowExpires() {
        var now = new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2026-10-24T00:00:00Z"));
        var clock = mock(java.time.Clock.class); when(clock.instant()).thenAnswer(call -> now.get());
        var auth = new BootstrapAccountAuthenticationService(config, accounts, clock);
        for (int i=0; i<5; i++) assertThatThrownBy(() -> auth.authenticate(EMAIL, "wrong"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        assertThatThrownBy(() -> auth.authenticate(EMAIL, "secret"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
        verifyNoInteractions(accounts);
        now.set(now.get().plusSeconds(61)); assertThat(auth.authenticate(EMAIL,"secret")).isNotNull();
    }
}
