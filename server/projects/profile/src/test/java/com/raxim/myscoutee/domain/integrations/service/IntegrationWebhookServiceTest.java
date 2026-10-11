package com.raxim.myscoutee.domain.integrations.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import com.raxim.myscoutee.domain.integrations.api.RoleAttentionEvents.RoleAttention;
import com.raxim.myscoutee.domain.integrations.document.IntegrationWebhookDelivery;
import com.raxim.myscoutee.domain.integrations.repository.IntegrationWebhookDeliveriesRepository;

@Tag("standard")
class IntegrationWebhookServiceTest {
    final IntegrationWebhookDeliveriesRepository deliveries = mock(IntegrationWebhookDeliveriesRepository.class);
    final McpEventsService events = mock(McpEventsService.class);
    final IntegrationWebhookService service = new IntegrationWebhookService(deliveries, mock(MongoTemplate.class), 1, 10);
    @BeforeEach void setup() { ReflectionTestUtils.setField(service, "mcpEvents", events); }
    @AfterEach void close() { service.shutdownDeliveryWorkers(); }
    private IntegrationWebhookDelivery pending() {
        var row = new IntegrationWebhookDelivery(); row.setProtocol(McpEventsService.PROTOCOL);
        row.setStatus("SENDING"); row.setAttemptCount(0); return row;
    }
    private void deliver(IntegrationWebhookDelivery row) { ReflectionTestUtils.invokeMethod(service, "deliver", row, null); }
    @Test void roleAttentionKeepsStableIdAndDuplicateDoesNotReplaceQueuedWork() {
        var row = pending();
        when(events.deliveries("admin", "admin.attention", "app-admin", "node", Map.of("kind", "report-submitted"), "notification"))
                .thenReturn(List.of(row));
        when(deliveries.insert(row)).thenThrow(new org.springframework.dao.DuplicateKeyException("existing"));
        service.enqueueRoleAttention(new RoleAttention("notification", "app-admin", "report-submitted", List.of("admin")));
        verify(deliveries).insert(row); verify(deliveries, never()).save(any());
    }
    @Test void unrelatedNotificationsDoNotQuerySubscriptions() {
        service.enqueueRoleAttention(new RoleAttention("n", "member", "report-submitted", List.of("member")));
        service.enqueueRoleAttention(new RoleAttention("n", "operator", "report-submitted", List.of("operator")));
        verifyNoInteractions(events, deliveries);
    }
    @Test void localThrottlingDoesNotSpendAttemptsAndPermanentFailuresDoNotRetry() throws Exception {
        var row = pending(); row.setAttemptCount(1);
        when(events.deliver(eq(row), isNull())).thenThrow(new McpEventCallbackClient.Throttled()).thenReturn(413);
        deliver(row); assertThat(row.getStatus()).isEqualTo("RETRY"); assertThat(row.getAttemptCount()).isEqualTo(1);
        deliver(row); assertThat(row.getStatus()).isEqualTo("FAILED"); assertThat(row.getLastError()).isEqualTo("http_413");
        assertThat(row.getNextAttemptAtIso()).isNull();
    }
    @Test void transientFailuresRetryTwiceThenBecomeTerminal() throws Exception {
        var row = pending(); when(events.deliver(eq(row), isNull())).thenReturn(503);
        deliver(row); assertThat(row.getStatus()).isEqualTo("RETRY"); assertThat(row.getAttemptCount()).isEqualTo(1);
        deliver(row); assertThat(row.getStatus()).isEqualTo("RETRY"); assertThat(row.getAttemptCount()).isEqualTo(2);
        deliver(row); assertThat(row.getStatus()).isEqualTo("FAILED"); assertThat(row.getAttemptCount()).isEqualTo(3);
        assertThat(row.getNextAttemptAtIso()).isNull();
    }
    @Test void successfulCallbackMarksSentAndUnknownProtocolDoesNotSend() throws Exception {
        var row = pending(); when(events.deliver(eq(row), isNull())).thenReturn(204);
        deliver(row); assertThat(row.getStatus()).isEqualTo("SENT"); assertThat(row.getSentAtIso()).isNotBlank();
        var unknown = pending(); unknown.setProtocol("unknown"); deliver(unknown);
        assertThat(unknown.getStatus()).isEqualTo("FAILED"); verify(events, never()).deliver(eq(unknown), any());
    }
}
