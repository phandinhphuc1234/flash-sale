package com.philia.flashsale.payment.configuration;

import java.util.HashMap;
import java.util.Map;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/** Shared Avro producer wiring used by both Payment DLT recovery and the result outbox. */
@Configuration(proxyBeanMethods = false)
public class PaymentKafkaProducerConfiguration {

    @Bean(name = "paymentKafkaProducerFactory")
    ProducerFactory<String, SpecificRecord> paymentKafkaProducerFactory(KafkaProperties properties) {
        return new DefaultKafkaProducerFactory<>(paymentProducerProperties(properties));
    }

    /** Exposes the effective producer policy for configuration tests without requiring Kafka. */
    public Map<String, Object> paymentProducerProperties(KafkaProperties properties) {
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

    @Bean(name = "paymentKafkaTemplate")
    KafkaTemplate<String, SpecificRecord> paymentKafkaTemplate(
            @Qualifier("paymentKafkaProducerFactory") ProducerFactory<String, SpecificRecord> factory) {
        return new KafkaTemplate<>(factory);
    }
}
