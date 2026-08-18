package com.philia.flashsale.payment.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;
import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxPublicationResult;
import com.philia.flashsale.payment.outbox.application.port.ClaimPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.PublishPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.UpdatePaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.service.PaymentOutboxPublicationService;
import com.philia.flashsale.payment.outbox.application.service.PaymentOutboxRetryPolicy;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit proof for sequential publication, stable retry identity, lease loss, and redaction. */
class PaymentOutboxPublicationServiceTests {

    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");

    @Test
    void publishesInClaimOrderAndMarksAfterBrokerAcknowledgement() {
        Fixtures fixtures = new Fixtures();
        PaymentOutboxEvent first = event("PaymentSucceeded");
        PaymentOutboxEvent second = event("PaymentFailed");
        when(fixtures.claim.claimBatch(any(), eq(100), eq("worker-1"), eq(NOW.plusSeconds(30))))
                .thenReturn(List.of(first, second));
        when(fixtures.clock.now()).thenReturn(NOW);
        when(fixtures.update.markPublished(any(), eq("worker-1"), eq(NOW))).thenReturn(true);

        PaymentOutboxPublicationResult result = fixtures.service().publishDue();

        assertThat(result).isEqualTo(new PaymentOutboxPublicationResult(2, 2, 0, 0));
        verify(fixtures.publisher).publish(first);
        verify(fixtures.publisher).publish(second);
        verify(fixtures.update).markPublished(first.eventId(), "worker-1", NOW);
        verify(fixtures.update).markPublished(second.eventId(), "worker-1", NOW);
    }

    @Test
    void retriesSameEventAfterBrokerFailureWithCappedSanitizedError() {
        Fixtures fixtures = new Fixtures();
        PaymentOutboxEvent event = event("PaymentSucceeded");
        when(fixtures.claim.claimBatch(any(), eq(100), eq("worker-1"), eq(NOW.plusSeconds(30))))
                .thenReturn(List.of(event));
        when(fixtures.clock.now()).thenReturn(NOW);
        doThrow(new IllegalStateException("secret=sk_test_123\nregistry unavailable"))
                .when(fixtures.publisher).publish(event);
        when(fixtures.update.recordFailure(eq(event.eventId()), eq("worker-1"), eq(NOW), anyString(),
                eq(NOW.plusSeconds(2)))).thenReturn(true);

        PaymentOutboxPublicationResult result = fixtures.service().publishDue();

        assertThat(result.retried()).isEqualTo(1);
        var error = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fixtures.update).recordFailure(eq(event.eventId()), eq("worker-1"), eq(NOW), error.capture(),
                eq(NOW.plusSeconds(2)));
        assertThat(error.getValue()).doesNotContain("sk_test_123").doesNotContain("\n")
                .contains("[REDACTED]");
        verify(fixtures.update, never()).markPublished(any(), anyString(), any());
    }

    @Test
    void keepsRowReclaimableWhenClaimOrFailureUpdateIsUnavailable() {
        Fixtures fixtures = new Fixtures();
        when(fixtures.clock.now()).thenReturn(NOW);
        when(fixtures.claim.claimBatch(any(), eq(100), eq("worker-1"), eq(NOW.plusSeconds(30))))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(fixtures.service().publishDue())
                .isEqualTo(new PaymentOutboxPublicationResult(0, 0, 0, 0));
        verify(fixtures.publisher, never()).publish(any());
    }

    @Test
    void reportsLostLeaseInsteadOfClaimingPublicationWasDurable() {
        Fixtures fixtures = new Fixtures();
        PaymentOutboxEvent event = event("PaymentFailed");
        when(fixtures.clock.now()).thenReturn(NOW);
        when(fixtures.claim.claimBatch(any(), eq(100), eq("worker-1"), eq(NOW.plusSeconds(30))))
                .thenReturn(List.of(event));
        when(fixtures.update.markPublished(event.eventId(), "worker-1", NOW)).thenReturn(false);

        assertThat(fixtures.service().publishDue().leaseLost()).isEqualTo(1);
    }

    @Test
    void capsExponentialRetryDelay() {
        assertThat(PaymentOutboxRetryPolicy.nextAttemptAt(NOW, 10, Duration.ofSeconds(60)))
                .isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void rejectsInvalidOperationalSettings() {
        Fixtures fixtures = new Fixtures();
        assertThatThrownBy(() -> new PaymentOutboxPublicationService(fixtures.claim, fixtures.update,
                fixtures.publisher, fixtures.clock, Duration.ZERO, 100, Duration.ofSeconds(60), "worker-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claim lease");
    }

    private PaymentOutboxEvent event(String eventType) {
        return new PaymentOutboxEvent(UUID.randomUUID(), UUID.randomUUID(), 1, eventType, 1,
                "flashsale.payment.events.v1", UUID.randomUUID(), "{}", null, null, "IN_PROGRESS", 1,
                NOW, "worker-1", NOW.plusSeconds(30), null, NOW, null);
    }

    private static final class Fixtures {
        private final ClaimPaymentOutboxPort claim = mock(ClaimPaymentOutboxPort.class);
        private final UpdatePaymentOutboxPort update = mock(UpdatePaymentOutboxPort.class);
        private final PublishPaymentOutboxPort publisher = mock(PublishPaymentOutboxPort.class);
        private final PaymentClockPort clock = mock(PaymentClockPort.class);

        private PaymentOutboxPublicationService service() {
            return new PaymentOutboxPublicationService(claim, update, publisher, clock,
                    Duration.ofSeconds(30), 100, Duration.ofSeconds(60), "worker-1");
        }
    }
}
