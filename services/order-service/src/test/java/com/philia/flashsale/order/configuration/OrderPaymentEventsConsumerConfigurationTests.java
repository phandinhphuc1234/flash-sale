package com.philia.flashsale.order.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.observability.OrderObservability;
import com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka.PaymentSucceededRecordException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

class OrderPaymentEventsConsumerConfigurationTests {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void nonRetryablePaymentRecordIsRecoveredToTheOrderOwnedDlt() {
        var configuration = new OrderPaymentEventsConsumerConfiguration();
        KafkaOperations<Object, Object> operations = mock(KafkaOperations.class);
        var properties = new OrderKafkaProperties("localhost:9092", "http://localhost:8081",
                "flashsale.purchase.events.v1", "accepted-v1", "accepted.dlt.v1",
                "flashsale.order.events.v1", List.of(Duration.ofSeconds(1)),
                "flashsale.payment.commands.v1");
        when(operations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var handler = configuration.orderPaymentSucceededErrorHandler(
                operations, properties, OrderObservability.noop());
        var record = new ConsumerRecord<String, Object>(
                properties.paymentEventsTopic(), 3, 12, "order-id", "invalid");

        boolean handled = handler.handleOne(new PaymentSucceededRecordException("invalid record"),
                record, mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(handled).isTrue();
        assertThat(handler.isAckAfterHandle()).isTrue();
        var published = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(operations).send(published.capture());
        assertThat(published.getValue().topic()).isEqualTo(properties.paymentEventsDltTopic());
    }
}
