package com.philia.flashsale.order.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.order.observability.OrderObservability;
import com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka.RegularStockHoldConfirmedConflictException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

/** Verifies bounded retry/recovery wiring for the Order regular-hold result consumer. */
class OrderRegularHoldResultsConsumerConfigurationTests {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void identityConflictIsRecoveredToTheOrderOwnedDlt() {
        var configuration = new OrderRegularHoldResultsConsumerConfiguration();
        KafkaOperations<Object, Object> operations = mock(KafkaOperations.class);
        var properties = new OrderKafkaProperties("localhost:9092", "http://localhost:8081",
                "accepted", "accepted-group", "accepted.dlt", "orders",
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10)), "payment.commands");
        when(operations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var handler = configuration.orderRegularHoldResultErrorHandler(operations, properties,
                OrderObservability.noop());
        var record = new ConsumerRecord<String, Object>(properties.regularHoldEventsTopic(), 2, 17L,
                "order-id", "invalid");

        boolean handled = handler.handleOne(new RegularStockHoldConfirmedConflictException("identity conflict"),
                record, mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(handled).isTrue();
        assertThat(handler.isAckAfterHandle()).isTrue();
        verify(operations).send(any(ProducerRecord.class));
    }
}
