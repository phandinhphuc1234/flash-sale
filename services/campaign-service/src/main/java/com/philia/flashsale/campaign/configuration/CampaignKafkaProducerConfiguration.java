package com.philia.flashsale.campaign.configuration;

import java.util.HashMap;
import java.util.Map;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Owns the Campaign publisher's wire-format settings.
 *
 * <p>Business code only sees the outbox publishing port. This configuration keeps the
 * Schema Registry and producer guarantees at the messaging adapter boundary.</p>
 */
@Configuration
public class CampaignKafkaProducerConfiguration {

    @Bean(name = "campaignKafkaProducerFactory")
    ProducerFactory<String, SpecificRecord> campaignKafkaProducerFactory(KafkaProperties properties) {
        return new DefaultKafkaProducerFactory<>(campaignProducerProperties(properties));
    }

    /** Returns the effective producer settings so configuration tests do not need a broker. */
    public Map<String, Object> campaignProducerProperties(KafkaProperties properties) {
        Map<String, Object> producerProperties = new HashMap<>(properties.buildProducerProperties());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.confluent.kafka.serializers.KafkaAvroSerializer");
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProperties.put("auto.register.schemas", false);
        producerProperties.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        return producerProperties;
    }

    @Bean(name = "campaignKafkaTemplate")
    KafkaTemplate<String, SpecificRecord> campaignKafkaTemplate(
            ProducerFactory<String, SpecificRecord> campaignKafkaProducerFactory) {
        return new KafkaTemplate<>(campaignKafkaProducerFactory);
    }
}
