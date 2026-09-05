package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.observability.OrderObservability;
import com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka.RegularStockHoldConfirmedConflictException;
import com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka.RegularStockHoldConfirmedRecordException;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

/** Retry/DLT wiring for Inventory regular-hold results consumed by the Order-owned Saga. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"order.creation.enabled", "order.regular-purchase.runtime.hold-result-consumer-enabled"},
        havingValue = "true")
public class OrderRegularHoldResultsConsumerConfiguration {
    @Bean(name = "orderRegularHoldResultConsumerFactory")
    ConsumerFactory<String, Object> orderRegularHoldResultConsumerFactory(KafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean(name = "orderRegularHoldResultKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, Object> orderRegularHoldResultKafkaListenerContainerFactory(
            @Qualifier("orderRegularHoldResultConsumerFactory") ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler orderRegularHoldResultErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(orderRegularHoldResultErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler orderRegularHoldResultErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
            OrderKafkaProperties properties, OrderObservability observability) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> {
                    observability.recordDltPublication(OrderObservability.ConsumerBoundary.REGULAR_HOLD_RESULTS);
                    return new TopicPartition(properties.regularHoldEventsDltTopic(), record.partition());
                });
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer,
                new OrderKafkaConsumerConfiguration.OrderKafkaRetryBackOff(properties.retryDelays()));
        handler.addNotRetryableExceptions(RegularStockHoldConfirmedRecordException.class,
                RegularStockHoldConfirmedConflictException.class,
                com.philia.flashsale.order.purchasesaga.application.exception.InvalidRegularHoldConfirmationException.class);
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        return handler;
    }
}
