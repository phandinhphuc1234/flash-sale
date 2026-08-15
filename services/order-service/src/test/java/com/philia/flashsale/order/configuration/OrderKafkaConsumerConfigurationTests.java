package com.philia.flashsale.order.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

class OrderKafkaConsumerConfigurationTests {

    @Test
    void retryPolicyUsesOneThreeAndTenSecondsThenStops() {
        var backOff = new OrderKafkaConsumerConfiguration.OrderKafkaRetryBackOff(
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10)));
        var execution = backOff.start();

        assertThat(execution.nextBackOff()).isEqualTo(1_000);
        assertThat(execution.nextBackOff()).isEqualTo(3_000);
        assertThat(execution.nextBackOff()).isEqualTo(10_000);
        assertThat(execution.nextBackOff()).isEqualTo(-1);
    }

    @Test
    void listenerFactoryUsesManualImmediateAcknowledgementAndDeliveryMetadata() {
        var configuration = new OrderKafkaConsumerConfiguration();
        @SuppressWarnings("unchecked")
        ConsumerFactory<String, PurchaseAcceptedV1> consumerFactory = mock(ConsumerFactory.class);
        var handler = new org.springframework.kafka.listener.DefaultErrorHandler(
                (ConsumerRecordRecoverer) (record, exception) -> { });

        var factory = configuration.orderPurchaseAcceptedKafkaListenerContainerFactory(consumerFactory, handler);

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(AckMode.MANUAL_IMMEDIATE);
        assertThat(factory.getContainerProperties().isObservationEnabled()).isTrue();
        assertThat(factory.getContainerProperties().isDeliveryAttemptHeader()).isTrue();
    }
}
