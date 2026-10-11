package com.raxim.myscoutee.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("standard")
class PlatformParametersServiceTest {
    @Test
    void missingDeploymentSettingsKeepTheNativeDeliveryDefaults() {
        var service = new PlatformParametersService(new NotificationParameters(
                null, null, null, null, null, null, null, null, null));
        assertThat(service.notificationWindowStartHour(0)).isZero();
        assertThat(service.notificationWindowEndHour(24)).isEqualTo(24);
        assertThat(service.notificationMaxWorkers(4)).isEqualTo(4);
        assertThat(service.notificationMaxRetries(5)).isEqualTo(5);
        assertThat(service.notificationInitialBackoff(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(60));
        assertThat(service.notificationCollapseWindowSeconds(300)).isEqualTo(300);
        assertThat(service.notificationTopicThreshold(1000)).isEqualTo(1000);
        assertThat(service.notificationMulticastThreshold(3)).isEqualTo(3);
        assertThat(service.notificationOutboxPollDelayMs(60_000)).isEqualTo(60_000);
    }

    @Test
    void invalidSettingsCannotCreateUnboundedWorkersOrBusyPolling() {
        var service = new PlatformParametersService(new NotificationParameters(
                -1, 25, 1000, 21, 0, -1, 0, 1001, 1));
        assertThat(service.notificationWindowStartHour(-10)).isZero();
        assertThat(service.notificationWindowEndHour(100)).isEqualTo(24);
        assertThat(service.notificationMaxWorkers(1000)).isEqualTo(64);
        assertThat(service.notificationMaxRetries(-1)).isZero();
        assertThat(service.notificationInitialBackoff(Duration.ZERO)).isEqualTo(Duration.ofSeconds(1));
        assertThat(service.notificationCollapseWindowSeconds(-1)).isZero();
        assertThat(service.notificationTopicThreshold(0)).isEqualTo(1);
        assertThat(service.notificationMulticastThreshold(1001)).isEqualTo(1000);
        assertThat(service.notificationOutboxPollDelayMs(0)).isEqualTo(1000);
    }

    @Test
    void explicitSettingsOverrideFallbacksWithinNativeLimits() {
        var service = new PlatformParametersService(new NotificationParameters(
                8, 20, 2, 3, 30, 120, 500, 5, 30_000));
        assertThat(service.notificationWindowStartHour(0)).isEqualTo(8);
        assertThat(service.notificationWindowEndHour(24)).isEqualTo(20);
        assertThat(service.notificationMaxWorkers(4)).isEqualTo(2);
        assertThat(service.notificationMaxRetries(5)).isEqualTo(3);
        assertThat(service.notificationInitialBackoff(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(30));
        assertThat(service.notificationCollapseWindowSeconds(300)).isEqualTo(120);
        assertThat(service.notificationTopicThreshold(1000)).isEqualTo(500);
        assertThat(service.notificationMulticastThreshold(3)).isEqualTo(5);
        assertThat(service.notificationOutboxPollDelayMs(60_000)).isEqualTo(30_000);
    }
}
