package com.philia.flashsale.inventory.configuration;

import java.util.HashMap;
import java.util.Map;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/** Isolated Avro producer wiring used only when the durable regular-hold outbox relay is enabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.outbox-publisher-enabled", havingValue = "true")
public class InventoryRegularHoldKafkaProducerConfiguration {
    @Bean
    ProducerFactory<String, SpecificRecord> inventoryRegularHoldProducerFactory(KafkaProperties properties) {
        Map<String, Object> producer = new HashMap<>(properties.buildProducerProperties());
        producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.confluent.kafka.serializers.KafkaAvroSerializer");
        producer.put(ProducerConfig.ACKS_CONFIG, "all");
        producer.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producer.put("auto.register.schemas", false);
        producer.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        return new DefaultKafkaProducerFactory<>(producer);
    }

    @Bean
    KafkaTemplate<String, SpecificRecord> inventoryRegularHoldKafkaTemplate(
            ProducerFactory<String, SpecificRecord> inventoryRegularHoldProducerFactory) {
        return new KafkaTemplate<>(inventoryRegularHoldProducerFactory);
    }
}
