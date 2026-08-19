package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.PaymentRequestedConflictException;
import com.philia.flashsale.payment.payment.adapter.in.messaging.kafka.PaymentRequestedRecordException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.beans.factory.annotation.Qualifier;
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

/** Kafka consumer wiring for the durable PaymentRequested command boundary. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "payment.kafka.consumer-enabled", havingValue = "true")
public class PaymentKafkaConsumerConfiguration {

    @Bean(name = "paymentRequestedConsumerFactory")
    ConsumerFactory<String, PaymentRequestedV1> paymentRequestedConsumerFactory(KafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean(name = "paymentRequestedKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, PaymentRequestedV1>
            paymentRequestedKafkaListenerContainerFactory(
                    ConsumerFactory<String, PaymentRequestedV1> consumerFactory,
                    DefaultErrorHandler paymentRequestedErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, PaymentRequestedV1>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.setCommonErrorHandler(paymentRequestedErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler paymentRequestedErrorHandler(
            @Qualifier("paymentKafkaTemplate") KafkaOperations<String, SpecificRecord> kafkaOperations,
            PaymentKafkaProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> new TopicPartition(properties.commandDltTopic(), record.partition()));
        var handler = new DefaultErrorHandler(recoverer,
                new PaymentKafkaRetryBackOff(properties.retryDelays()));
        handler.addNotRetryableExceptions(PaymentRequestedRecordException.class,
                PaymentRequestedConflictException.class);
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        return handler;
    }

    /** Supplies the approved 1/3/10-second retry sequence after initial delivery. */
    static final class PaymentKafkaRetryBackOff implements BackOff {
        private final List<Duration> retryDelays;

        PaymentKafkaRetryBackOff(List<Duration> retryDelays) {
            if (retryDelays == null || retryDelays.isEmpty()
                    || retryDelays.stream().anyMatch(delay -> delay == null || delay.isZero()
                            || delay.isNegative())) {
                throw new IllegalArgumentException("Payment Kafka retry delays must be positive");
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
