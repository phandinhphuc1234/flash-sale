package com.philia.flashsale.cart.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.cart.adapter.in.messaging.kafka.CartReconciliationRecordException;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

/** Verifies Cart's bounded retry and consumer-specific DLT recovery wiring. */
class CartReconciliationConfigurationTests {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void malformedRecordIsRecoveredToTheConfiguredCartDlt() {
        var configuration = new CartReconciliationConfiguration();
        KafkaOperations<Object, Object> operations = mock(KafkaOperations.class);
        when(operations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var handler = configuration.cartReconciliationErrorHandler(operations,
                "flashsale.cart.checkout-reconciliation.dlt.v1");
        var record = new ConsumerRecord<String, Object>("flashsale.cart.checkout.commands.v1", 3, 18L,
                "cart-id", "invalid");

        boolean handled = handler.handleOne(new CartReconciliationRecordException("invalid envelope"), record,
                mock(Consumer.class), mock(MessageListenerContainer.class));

        assertThat(handled).isTrue();
        assertThat(handler.isAckAfterHandle()).isTrue();
        verify(operations).send(any(ProducerRecord.class));
    }
}
