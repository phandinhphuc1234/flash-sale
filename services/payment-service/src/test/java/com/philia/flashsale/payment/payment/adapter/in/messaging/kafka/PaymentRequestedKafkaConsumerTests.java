package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

import static com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support.PaymentRequestedKafkaTestFixtures.command;
import static com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.support.PaymentRequestedKafkaTestFixtures.consumerRecord;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult;
import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

/** Consumer policy tests for acknowledgement timing and poison/conflict classification. */
class PaymentRequestedKafkaConsumerTests {

    private final PaymentRequestedAvroMapper mapper = mock(PaymentRequestedAvroMapper.class);
    private final AcceptPaymentRequestUseCase useCase = mock(AcceptPaymentRequestUseCase.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final PaymentRequestedKafkaConsumer consumer = new PaymentRequestedKafkaConsumer(mapper, useCase);

    @Test
    void acknowledgesCreatedReplayAndExpiredResultsOnlyAfterUseCaseReturns() {
        var command = command();
        stubMapper(command);
        when(useCase.accept(command)).thenReturn(AcceptPaymentRequestResult.accepted(
                UUID.randomUUID(), "a".repeat(64), PaymentStatus.PENDING));
        consumer.onMessage(consumerRecord(UUID.randomUUID(), null), acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void conflictIsNonRetryableAndIsNotAcknowledgedByTheAdapter() {
        var command = command();
        stubMapper(command);
        when(useCase.accept(command)).thenReturn(AcceptPaymentRequestResult.conflict(
                UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "contradictory snapshot"));
        assertThatThrownBy(() -> consumer.onMessage(consumerRecord(UUID.randomUUID(), null), acknowledgment))
                .isInstanceOf(PaymentRequestedConflictException.class);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void poisonMapperFailureIsPropagatedWithoutAcknowledgement() {
        when(mapper.map(any())).thenThrow(new PaymentRequestedRecordException("invalid key"));

        assertThatThrownBy(() -> consumer.onMessage(consumerRecord(UUID.randomUUID(), null), acknowledgment))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessage("invalid key");
        verify(useCase, never()).accept(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void storageFailureLeavesOffsetUnacknowledgedForConfiguredRetry() {
        var command = command();
        stubMapper(command);
        when(useCase.accept(command)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> consumer.onMessage(consumerRecord(UUID.randomUUID(), null), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(acknowledgment, never()).acknowledge();
    }

    private void stubMapper(com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand command) {
        when(mapper.map(any())).thenReturn(command);
    }
}
