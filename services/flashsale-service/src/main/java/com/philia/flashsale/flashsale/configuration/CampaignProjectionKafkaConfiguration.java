package com.philia.flashsale.flashsale.configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.specific.SpecificRecord;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.kafka.KafkaException;

/** Configures the Campaign projection consumer's bounded, manual-acknowledgement policy. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(KafkaProperties.class)
public class CampaignProjectionKafkaConfiguration {

    @Bean(name = "campaignProjectionConsumerFactory")
    ConsumerFactory<String, SpecificRecord> campaignProjectionConsumerFactory(KafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean(name = "campaignProjectionKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, SpecificRecord>
            campaignProjectionKafkaListenerContainerFactory(
                    ConsumerFactory<String, SpecificRecord> campaignProjectionConsumerFactory,
                    CampaignProjectionProperties properties) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, SpecificRecord>();
        factory.setConsumerFactory(campaignProjectionConsumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.MANUAL);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        factory.setCommonErrorHandler(campaignProjectionErrorHandler(properties));
        return factory;
    }

    /** Bounded retries end by propagating the error, leaving the offset uncommitted. */
    DefaultErrorHandler campaignProjectionErrorHandler(CampaignProjectionProperties properties) {
        ConsumerRecordRecoverer noDltRecoverer = (record, exception) -> {
            throw new KafkaException("Campaign projection listener stopped after bounded retries", exception);
        };
        var handler = new DefaultErrorHandler(noDltRecoverer,
                new CampaignProjectionBackOff(properties.maxDeliveries(), properties.retryDelays()));
        handler.setCommitRecovered(false);
        handler.setAckAfterHandle(false);
        return handler;
    }

    /** Supplies the approved 250/500 ms retry sequence without introducing a DLT. */
    static final class CampaignProjectionBackOff implements BackOff {
        private final int maxDeliveries;
        private final List<Duration> retryDelays;

        CampaignProjectionBackOff(int maxDeliveries, List<Duration> retryDelays) {
            if (maxDeliveries < 1 || retryDelays == null || retryDelays.size() < maxDeliveries - 1) {
                throw new IllegalArgumentException("Campaign retry policy is incomplete");
            }
            this.maxDeliveries = maxDeliveries;
            this.retryDelays = List.copyOf(retryDelays);
        }

        @Override
        public BackOffExecution start() {
            return new BackOffExecution() {
                private int retryCount;

                @Override
                public long nextBackOff() {
                    if (retryCount >= maxDeliveries - 1) {
                        return STOP;
                    }
                    Duration delay = retryDelays.get(retryCount++);
                    if (delay.isNegative() || delay.isZero()) {
                        throw new IllegalStateException("Campaign retry delay must be positive");
                    }
                    return delay.toMillis();
                }
            };
        }
    }
}
