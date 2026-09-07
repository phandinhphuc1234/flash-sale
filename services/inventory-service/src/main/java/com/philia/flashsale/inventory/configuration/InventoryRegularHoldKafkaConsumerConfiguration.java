package com.philia.flashsale.inventory.configuration;

import com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka.RegularHoldCommandRecordException;
import com.philia.flashsale.inventory.regularhold.application.exception.RegularHoldCommandConflictException;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.avro.specific.SpecificRecord;
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

/** Kafka retry/DLT wiring for the strict, disabled-by-default regular hold command consumer. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.command-consumer-enabled", havingValue = "true")
public class InventoryRegularHoldKafkaConsumerConfiguration {
    @Bean
    ConsumerFactory<String, SpecificRecord> inventoryRegularHoldConsumerFactory(KafkaProperties properties) {
        Map<String, Object> consumer = new HashMap<>(properties.buildConsumerProperties());
        consumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumer.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, SpecificRecord>
            inventoryRegularHoldKafkaListenerContainerFactory(
                    ConsumerFactory<String, SpecificRecord> inventoryRegularHoldConsumerFactory,
                    DefaultErrorHandler inventoryRegularHoldErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, SpecificRecord>();
        factory.setConsumerFactory(inventoryRegularHoldConsumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(inventoryRegularHoldErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler inventoryRegularHoldErrorHandler(KafkaOperations<?, ?> kafkaOperations,
            InventoryRegularHoldProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, exception) -> dltDestination(record, properties));
        var handler = new DefaultErrorHandler(recoverer, new FixedDelays(properties.getRetryDelays()));
        handler.addNotRetryableExceptions(RegularHoldCommandRecordException.class,
                RegularHoldCommandConflictException.class);
        handler.setCommitRecovered(true);
        handler.setAckAfterHandle(true);
        return handler;
    }

    static TopicPartition dltDestination(ConsumerRecord<?, ?> record, InventoryRegularHoldProperties properties) {
        return new TopicPartition(properties.getCommandDltTopic(), record.partition());
    }

    static final class FixedDelays implements BackOff {
        private final List<Duration> delays;
        FixedDelays(List<Duration> delays) { this.delays = List.copyOf(delays); }
        @Override public BackOffExecution start() {
            return new BackOffExecution() {
                private int attempts;
                @Override public long nextBackOff() {
                    return attempts >= delays.size() ? STOP : delays.get(attempts++).toMillis();
                }
            };
        }
    }
}
