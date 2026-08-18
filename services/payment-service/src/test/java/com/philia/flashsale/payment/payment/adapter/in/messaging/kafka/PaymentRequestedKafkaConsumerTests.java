package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult;
import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

/** Consumer policy tests for acknowledgement timing and poison/conflict classification. */
class PaymentRequestedKafkaConsumerTests {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void acknowledgesCreatedReplayAndExpiredResultsOnlyAfterUseCaseReturns() {
        var mapper = org.mockito.Mockito.mock(PaymentRequestedAvroMapper.class);
        var useCase = org.mockito.Mockito.mock(AcceptPaymentRequestUseCase.class);
        var acknowledgment = org.mockito.Mockito.mock(Acknowledgment.class);
        var command = command();
        when(mapper.map(any())).thenReturn(command);
        when(useCase.accept(command)).thenReturn(AcceptPaymentRequestResult.accepted(
                UUID.randomUUID(), "a".repeat(64), PaymentStatus.PENDING));
        var consumer = new PaymentRequestedKafkaConsumer(mapper, useCase);

        consumer.onMessage(record(), acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void conflictIsNonRetryableAndIsNotAcknowledgedByTheAdapter() {
        var mapper = org.mockito.Mockito.mock(PaymentRequestedAvroMapper.class);
        var useCase = org.mockito.Mockito.mock(AcceptPaymentRequestUseCase.class);
        var acknowledgment = org.mockito.Mockito.mock(Acknowledgment.class);
        var command = command();
        when(mapper.map(any())).thenReturn(command);
        when(useCase.accept(command)).thenReturn(AcceptPaymentRequestResult.conflict(
                UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "contradictory snapshot"));
        var consumer = new PaymentRequestedKafkaConsumer(mapper, useCase);

        assertThatThrownBy(() -> consumer.onMessage(record(), acknowledgment))
                .isInstanceOf(PaymentRequestedConflictException.class);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void poisonMapperFailureIsPropagatedWithoutAcknowledgement() {
        var mapper = org.mockito.Mockito.mock(PaymentRequestedAvroMapper.class);
        var useCase = org.mockito.Mockito.mock(AcceptPaymentRequestUseCase.class);
        var acknowledgment = org.mockito.Mockito.mock(Acknowledgment.class);
        when(mapper.map(any())).thenThrow(new PaymentRequestedRecordException("invalid key"));
        var consumer = new PaymentRequestedKafkaConsumer(mapper, useCase);

        assertThatThrownBy(() -> consumer.onMessage(record(), acknowledgment))
                .isInstanceOf(PaymentRequestedRecordException.class)
                .hasMessage("invalid key");
        verify(useCase, never()).accept(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void storageFailureLeavesOffsetUnacknowledgedForConfiguredRetry() {
        var mapper = org.mockito.Mockito.mock(PaymentRequestedAvroMapper.class);
        var useCase = org.mockito.Mockito.mock(AcceptPaymentRequestUseCase.class);
        var acknowledgment = org.mockito.Mockito.mock(Acknowledgment.class);
        var command = command();
        when(mapper.map(any())).thenReturn(command);
        when(useCase.accept(command)).thenThrow(new IllegalStateException("database unavailable"));
        var consumer = new PaymentRequestedKafkaConsumer(mapper, useCase);

        assertThatThrownBy(() -> consumer.onMessage(record(), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<String, com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1> record() {
        return new ConsumerRecord<>("flashsale.payment.commands.v1", 0, 0L, UUID.randomUUID().toString(), null);
    }

    private AcceptPaymentRequestCommand command() {
        UUID orderId = UUID.randomUUID();
        return new AcceptPaymentRequestCommand(UUID.randomUUID(), "PaymentRequested", 1, "order-service", "ORDER",
                orderId, 1L, UUID.randomUUID(), UUID.randomUUID(), NOW, orderId, UUID.randomUUID(),
                new BigDecimal("1.0000"), "VND", NOW.plusSeconds(600), null, null);
    }
}
