package com.philia.flashsale.payment.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.payment.outbox.adapter.in.scheduling.PaymentOutboxPublisherJob;
import com.philia.flashsale.payment.outbox.adapter.out.messaging.kafka.KafkaPaymentResultPublisher;
import com.philia.flashsale.payment.outbox.adapter.out.messaging.kafka.PaymentResultAvroMapper;
import com.philia.flashsale.payment.outbox.application.port.ClaimPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.PublishPaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.port.UpdatePaymentOutboxPort;
import com.philia.flashsale.payment.outbox.application.service.PaymentOutboxPublicationService;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition root for the disabled-by-default Payment result outbox relay. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = {"payment.acceptance.enabled", "payment.kafka.outbox-publisher-enabled"},
        havingValue = "true")
public class PaymentOutboxConfiguration {

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

    @Bean
    PaymentResultAvroMapper paymentResultAvroMapper(ObjectMapper objectMapper) {
        return new PaymentResultAvroMapper(objectMapper);
    }

    @Bean
    PublishPaymentOutboxPort publishPaymentOutboxPort(
            @Qualifier("paymentKafkaTemplate") KafkaTemplate<String, SpecificRecord> kafkaTemplate,
            PaymentResultAvroMapper mapper, PaymentKafkaProperties properties) {
        return new KafkaPaymentResultPublisher(kafkaTemplate, mapper, properties.eventTopic());
    }

    @Bean
    PaymentOutboxPublicationService paymentOutboxPublicationService(
            ClaimPaymentOutboxPort claimPort, UpdatePaymentOutboxPort updatePort,
            PublishPaymentOutboxPort publishPort, PaymentClockPort clock, PaymentKafkaProperties properties,
            @Value("${HOSTNAME:payment-service}") String hostName) {
        return new PaymentOutboxPublicationService(claimPort, updatePort, publishPort, clock,
                properties.outboxClaimLease(), properties.outboxBatchSize(), properties.outboxRetryBackoffCap(),
                normalizeWorkerId(hostName) + "-" + UUID.randomUUID());
    }

    @Bean
    PaymentOutboxPublisherJob paymentOutboxPublisherJob(
            PaymentOutboxPublicationService publicationService) {
        return new PaymentOutboxPublisherJob(publicationService);
    }

    private String normalizeWorkerId(String hostName) {
        if (hostName == null || hostName.isBlank()) {
            return "payment-service";
        }
        return hostName.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
