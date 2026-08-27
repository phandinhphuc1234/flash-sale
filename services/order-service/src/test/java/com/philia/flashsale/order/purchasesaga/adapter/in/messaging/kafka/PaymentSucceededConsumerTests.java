package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededDataV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import com.philia.flashsale.order.observability.OrderObservability;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentFailureUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

class PaymentSucceededConsumerTests {
    private static final String TOPIC = "flashsale.payment.events.v1";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsTraceHeadersAndAcknowledgesOnlyAfterTheUseCaseSucceeds() {
        UUID orderId = UUID.randomUUID();
        UUID sagaId = orderId;
        var success = mock(ApplyPaymentSuccessUseCase.class);
        var acknowledgment = mock(Acknowledgment.class);
        var command = ArgumentCaptor.forClass(PaymentSucceededCommand.class);
        when(success.apply(any())).thenReturn(PaymentSuccessResult.applied(orderId, sagaId, UUID.randomUUID()));
        var record = record(orderId, new BigDecimal("20.0000"), "VND");
        record.headers().add(new RecordHeader("traceparent",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
                        .getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("tracestate", "vendor=value".getBytes(StandardCharsets.UTF_8)));

        consumer(success).onMessage(asObjectRecord(record), acknowledgment);

        verify(success).apply(command.capture());
        assertThat(command.getValue().traceparent())
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(command.getValue().tracestate()).isEqualTo("vendor=value");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void conflictIsNonAcknowledgedSoTheConfiguredErrorHandlerCanRouteItToTheDlt() {
        UUID orderId = UUID.randomUUID();
        var success = mock(ApplyPaymentSuccessUseCase.class);
        var acknowledgment = mock(Acknowledgment.class);
        when(success.apply(any())).thenReturn(PaymentSuccessResult.conflict(
                orderId, orderId, "payment identity conflict"));

        assertThatThrownBy(() -> consumer(success).onMessage(asObjectRecord(
                record(orderId, new BigDecimal("20.0000"), "VND")), acknowledgment))
                .isInstanceOf(PaymentSucceededConflictException.class)
                .hasMessageContaining("payment identity conflict");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void rejectsInvalidAmountAndCurrencyAtTheKafkaBoundary() {
        UUID orderId = UUID.randomUUID();
        var mapper = new PaymentSucceededAvroMapper(TOPIC);

        assertThatThrownBy(() -> mapper.map(record(orderId, new BigDecimal("20.00001"), "VND")))
                .isInstanceOf(PaymentSucceededRecordException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> mapper.map(record(orderId, new BigDecimal("20.0000"), "vnd")))
                .isInstanceOf(PaymentSucceededRecordException.class)
                .hasMessageContaining("currency");
    }

    private PaymentSucceededKafkaConsumer consumer(ApplyPaymentSuccessUseCase success) {
        return new PaymentSucceededKafkaConsumer(new PaymentSucceededAvroMapper(TOPIC), success,
                mock(PaymentFailedAvroMapper.class), mock(ApplyPaymentFailureUseCase.class),
                OrderObservability.noop());
    }

    private ConsumerRecord<String, PaymentSucceededV1> record(
            UUID orderId, BigDecimal amount, String currency) {
        UUID paymentId = UUID.randomUUID();
        PaymentSucceededV1 event = new PaymentSucceededV1(UUID.randomUUID(), "PaymentSucceeded", 1,
                "payment-service", "PAYMENT", paymentId, 2L, orderId, UUID.randomUUID(), NOW,
                new PaymentSucceededDataV1(paymentId, orderId, amount, currency, NOW.plusSeconds(1),
                        "stripe", "cs_test_123", "pi_test_123"));
        return new ConsumerRecord<>(TOPIC, 1, 12, orderId.toString(), event);
    }

    @SuppressWarnings("unchecked")
    private ConsumerRecord<String, Object> asObjectRecord(
            ConsumerRecord<String, PaymentSucceededV1> record) {
        return (ConsumerRecord<String, Object>) (ConsumerRecord<?, ?>) record;
    }
}
