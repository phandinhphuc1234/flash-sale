package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka.ConfirmReservationRecordException;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka.ReservationCommandProperties;
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
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/** Bounded retry/DLT policy for Order-owned reservation commands. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.reservation-commands.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationCommandKafkaConfiguration {
    @Bean(name = "flashSaleReservationCommandConsumerFactory")
    ConsumerFactory<String, ConfirmPurchaseReservationV1> flashSaleReservationCommandConsumerFactory(
            KafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean(name = "flashSaleReservationCommandKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, ConfirmPurchaseReservationV1>
            flashSaleReservationCommandKafkaListenerContainerFactory(
                    ConsumerFactory<String, ConfirmPurchaseReservationV1> consumerFactory,
                    DefaultErrorHandler flashSaleReservationCommandErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ConfirmPurchaseReservationV1>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(flashSaleReservationCommandErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler flashSaleReservationCommandErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
            ReservationCommandProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(properties.dltTopic(), record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new RetryBackOff(properties.retryDelays()));
        handler.addNotRetryableExceptions(ConfirmReservationRecordException.class);
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
