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
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition root for the disabled-by-default Payment result outbox relay. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = {"payment.acceptance.enabled", "payment.kafka.outbox-publisher-enabled"},
        havingValue = "true")
public class PaymentOutboxConfiguration {

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
