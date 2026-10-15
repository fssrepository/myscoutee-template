package com.raxim.myscoutee.domain.integrations.service;

import static org.assertj.core.api.Assertions.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("standard")
class McpEventCallbackClientTest {
    @Test void callbackPermitsBoundConcurrencyAndReleaseOnlyOnce() throws Exception {
        var client = new McpEventCallbackClient(100, 100, 8);
        try (AutoCloseable cleanup = client::close) {
            var permits = new java.util.ArrayList<McpEventCallbackClient.DeliveryPermit>();
            for (int i = 0; i < 8; i++) {
                var permit = client.reserveDelivery();
                assertThat(permit).isNotNull();
                permits.add(permit);
            }
            assertThat(client.reserveDelivery()).isNull();
            permits.getFirst().close();
            permits.getFirst().close();
            var replacement = client.reserveDelivery();
            assertThat(replacement).isNotNull();
            assertThat(client.reserveDelivery()).isNull();
            replacement.close();
            permits.forEach(McpEventCallbackClient.DeliveryPermit::close);
        }
    }

    @Test void configuredConcurrencyHasOneSlotFloorAndThirtyTwoSlotCeiling() throws Exception {
        for (int configured : new int[] {0, 3, 1000}) {
            int expected = configured == 0 ? 1 : configured == 3 ? 3 : 32;
            var client = new McpEventCallbackClient(100, 100, configured);
            try (AutoCloseable cleanup = client::close) {
                var permits = new java.util.ArrayList<McpEventCallbackClient.DeliveryPermit>();
                for (int i = 0; i < expected; i++) {
                    var permit = client.reserveDelivery();
                    assertThat(permit).as("configured=%s slot=%s", configured, i).isNotNull();
                    permits.add(permit);
                }
                assertThat(client.reserveDelivery()).isNull();
                permits.forEach(McpEventCallbackClient.DeliveryPermit::close);
            }
        }
    }

    @Test void rateAndBurstOverridesRemainBoundedAndRefillWithoutQueueing() throws Exception {
        // Release each reservation so this measures rate admission, not concurrency.
        var overridden = new McpEventCallbackClient(2, 2, 8);
        try (AutoCloseable cleanup = overridden::close) {
            var client = overridden;
            client.reserveDelivery().close();
            client.reserveDelivery().close();
            assertThat(client.reserveDelivery()).isNull();
            Thread.sleep(550);
            var refilled = client.reserveDelivery();
            assertThat(refilled).isNotNull();
            refilled.close();
            assertThat(client.reserveDelivery()).isNull();
        }
        for (int configured : new int[] {0, 1000}) {
            var client = new McpEventCallbackClient(0, configured, 32);
            try (AutoCloseable cleanup = client::close) {
                int expected = configured == 0 ? 1 : 100;
                for (int i = 0; i < expected; i++) {
                    var permit = client.reserveDelivery();
                    assertThat(permit).isNotNull();
                    permit.close();
                }
                assertThat(client.reserveDelivery()).isNull();
            }
        }
    }
    @Test void standardWebhookSignatureCoversExactBytesIdAndSigningTime() {
        String secret = "whsec_" + Base64.getEncoder().encodeToString(new byte[32]);
        // Independent HMAC-SHA256 fixture, not computed by the implementation under test.
        assertThat(McpEventCallbackClient.sign(secret, "evt_123", "1791288000", "{\"x\":1}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("v1,g39bxcil793J5mBlJQQYt6YrRMlTXDxiSfBpAktTfr4=");
        assertThat(McpEventCallbackClient.sign(secret, "evt_124", "1791288000", "{\"x\":1}".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(McpEventCallbackClient.sign(secret, "evt_123", "1791288000", "{\"x\":1}".getBytes(StandardCharsets.UTF_8)));
    }
    @Test void callbacksNeverOptIntoLocalDevExceptionsOrRedirectCredentials() {
        for (String url : java.util.List.of("http://example.com/x", "https://user@example.com/x", "https://example.com/x#fragment",
                "https://127.0.0.1/x", "https://[::1]/x", "https://169.254.169.254/x", "https://100.64.0.1/x",
                "https://127.1/x", "https://2130706433/x", "https://0177.0.0.1/x", "https://0x7f000001/x",
                "https://0x7f.0.0.1/x", "https://008.008.008.008/x", "https://134744072/x"))
            assertThatThrownBy(() -> McpEventCallbackClient.callbackUri(url)).isInstanceOf(IllegalArgumentException.class);
        assertThat(McpEventCallbackClient.callbackUri("https://callback.example/x").getHost()).isEqualTo("callback.example");
        assertThat(McpEventCallbackClient.callbackUri("https://8.8.8.8/x").getHost()).isEqualTo("8.8.8.8");
    }
    @Test void resolverRejectsNonPublicRangesIncludingIpv6AndMappedIpv4() throws Exception {
        for (String ip : java.util.List.of("10.0.0.1", "172.16.1.2", "192.168.1.2", "192.0.2.1", "198.18.0.1",
                "224.0.0.1", "240.1.1.1", "::ffff:127.0.0.1", "fc00::1", "fe80::1", "2001:db8::1", "2002:7f00:1::"))
            assertThat(McpEventCallbackClient.publicAddress(InetAddress.getByName(ip))).as(ip).isFalse();
        for (String ip : java.util.List.of("8.8.8.8", "2606:4700:4700::1111"))
            assertThat(McpEventCallbackClient.publicAddress(InetAddress.getByName(ip))).as(ip).isTrue();
    }
    @Test void requiresTheSpecifiedSecretPrefixAndDecodedLength() {
        for (String secret : java.util.List.of("", "whsec_bad", "whsec_" + Base64.getEncoder().encodeToString(new byte[23]),
                "whsec_" + Base64.getEncoder().encodeToString(new byte[65])))
            assertThatThrownBy(() -> McpEventCallbackClient.key(secret)).isInstanceOf(IllegalArgumentException.class);
        assertThat(McpEventCallbackClient.key("whsec_" + Base64.getEncoder().encodeToString(new byte[24]))).hasSize(24);
    }
}
