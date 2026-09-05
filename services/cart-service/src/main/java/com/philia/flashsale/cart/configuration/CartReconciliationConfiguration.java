package com.philia.flashsale.cart.configuration;

import com.philia.flashsale.cart.adapter.in.messaging.kafka.CartReconciliationRecordException;
import com.philia.flashsale.cart.adapter.in.messaging.kafka.ReconcilePurchasedCartSnapshotAvroMapper;
import com.philia.flashsale.cart.application.port.in.ReconcilePurchasedCartSnapshotUseCase;
import com.philia.flashsale.cart.application.port.out.ReconcilePurchasedCartSnapshotPort;
import com.philia.flashsale.cart.application.usecase.ReconcilePurchasedCartSnapshotService;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.util.backoff.FixedBackOff;

/** Composition root for Cart's disabled-by-default reconciliation consumer. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cart.checkout.reconciliation.consumer-enabled", havingValue = "true")
public class CartReconciliationConfiguration {
    @Bean
    ReconcilePurchasedCartSnapshotAvroMapper reconcilePurchasedCartSnapshotAvroMapper() {
        return new ReconcilePurchasedCartSnapshotAvroMapper();
    }

    @Bean
    ReconcilePurchasedCartSnapshotService reconcilePurchasedCartSnapshotService(
            ReconcilePurchasedCartSnapshotPort persistence) {
        return new ReconcilePurchasedCartSnapshotService(persistence);
    }

    @Bean
    ReconcilePurchasedCartSnapshotUseCase reconcilePurchasedCartSnapshotUseCase(
            ReconcilePurchasedCartSnapshotService service) {
        return service;
    }

    @Bean(name = "cartReconciliationConsumerFactory")
    ConsumerFactory<String, Object> cartReconciliationConsumerFactory(KafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean(name = "cartReconciliationKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, Object> cartReconciliationKafkaListenerContainerFactory(
            @Qualifier("cartReconciliationConsumerFactory") ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler cartReconciliationErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(cartReconciliationErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler cartReconciliationErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
            @Value("${cart.checkout.reconciliation.dlt-topic}") String dltTopic) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(dltTopic, record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        handler.addNotRetryableExceptions(CartReconciliationRecordException.class,
                IllegalArgumentException.class);
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        return handler;
    }
}
