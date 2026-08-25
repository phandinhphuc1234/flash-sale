package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka.PaymentSucceededConflictException;
import com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka.PaymentSucceededRecordException;
import com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka.PaymentFailedConflictException;
import com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka.PaymentFailedRecordException;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/** Retry/DLT wiring for the Order-owned PaymentSucceeded boundary. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"order.creation.enabled", "order.runtime.payment-events-consumer-enabled"},
        havingValue = "true", matchIfMissing = true)
public class OrderPaymentEventsConsumerConfiguration {
    @Bean(name = "orderPaymentSucceededConsumerFactory")
    ConsumerFactory<String, Object> orderPaymentSucceededConsumerFactory(KafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean(name = "orderPaymentSucceededKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, Object>
            orderPaymentSucceededKafkaListenerContainerFactory(
                    @Qualifier("orderPaymentSucceededConsumerFactory") ConsumerFactory<String, Object> consumerFactory,
                    DefaultErrorHandler orderPaymentSucceededErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(orderPaymentSucceededErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler orderPaymentSucceededErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations, OrderKafkaProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> new TopicPartition(properties.paymentEventsDltTopic(), record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new RetryBackOff(properties.retryDelays()));
        handler.addNotRetryableExceptions(PaymentSucceededRecordException.class,
                PaymentSucceededConflictException.class, PaymentFailedRecordException.class,
                PaymentFailedConflictException.class);
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        return handler;
    }

    static final class RetryBackOff implements BackOff {
        private final List<Duration> delays;
        RetryBackOff(List<Duration> delays) { this.delays = List.copyOf(delays); }
        @Override public BackOffExecution start() {
            return new BackOffExecution() {
                private int index;
                @Override public long nextBackOff() {
                    return index < delays.size() ? delays.get(index++).toMillis() : STOP;
                }
            };
        }
    }
}
