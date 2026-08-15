package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.order.adapter.in.messaging.kafka.PurchaseAcceptedConflictException;
import com.philia.flashsale.order.order.adapter.in.messaging.kafka.PurchaseAcceptedRecordException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1;

/** Kafka consumer wiring for the durable PurchaseAccepted-to-Order boundary. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "order.runtime.accepted-purchase-consumer-enabled", havingValue = "true", matchIfMissing = true)
public class OrderKafkaConsumerConfiguration {

    @Bean(name = "orderPurchaseAcceptedConsumerFactory")
    ConsumerFactory<String, PurchaseAcceptedV1> orderPurchaseAcceptedConsumerFactory(KafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean(name = "orderPurchaseAcceptedKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, PurchaseAcceptedV1>
            orderPurchaseAcceptedKafkaListenerContainerFactory(
                    ConsumerFactory<String, PurchaseAcceptedV1> consumerFactory,
                    DefaultErrorHandler orderPurchaseAcceptedErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, PurchaseAcceptedV1>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.setCommonErrorHandler(orderPurchaseAcceptedErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler orderPurchaseAcceptedErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations, OrderKafkaProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> new TopicPartition(properties.acceptedPurchaseDltTopic(), record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new OrderKafkaRetryBackOff(properties.retryDelays()));
        handler.addNotRetryableExceptions(PurchaseAcceptedRecordException.class,
                PurchaseAcceptedConflictException.class);
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        return handler;
    }

    /** Supplies the approved 1/3/10-second retry sequence after the initial delivery. */
    static final class OrderKafkaRetryBackOff implements BackOff {
        private final List<Duration> retryDelays;

        OrderKafkaRetryBackOff(List<Duration> retryDelays) {
            if (retryDelays == null || retryDelays.isEmpty()
                    || retryDelays.stream().anyMatch(delay -> delay == null || delay.isZero() || delay.isNegative())) {
                throw new IllegalArgumentException("Order Kafka retry delays must be positive");
            }
            this.retryDelays = List.copyOf(retryDelays);
        }

        @Override
        public BackOffExecution start() {
            return new BackOffExecution() {
                private int retryCount;

                @Override
                public long nextBackOff() {
                    if (retryCount >= retryDelays.size()) {
                        return STOP;
                    }
                    return retryDelays.get(retryCount++).toMillis();
                }
            };
        }
    }
}
