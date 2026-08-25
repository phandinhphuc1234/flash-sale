package com.philia.flashsale.order.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderCreatedV1;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.order.outbox.adapter.in.scheduling.OrderOutboxPublisherJob;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaOrderCreatedPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderCreatedAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaPaymentRequestedPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.PaymentRequestedAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.ConfirmReservationAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaConfirmReservationPublisher;
import com.philia.flashsale.order.outbox.adapter.out.persistence.OrderOutboxPersistenceAdapter;
import com.philia.flashsale.order.outbox.application.port.ClaimOrderOutboxEventsPort;
import com.philia.flashsale.order.outbox.application.port.UpdateOrderOutboxPublicationPort;
import java.util.Map;
import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxPublicationService;
import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxRetryPolicy;
import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxEventTypeDispatcher;
import com.philia.flashsale.order.observability.OrderObservability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the Order outbox relay only when the runtime relay switches are enabled. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnExpression("'${order.runtime.outbox-publisher-enabled:true}' == 'true' && '${order.outbox.enabled:true}' == 'true'")
public class OrderOutboxConfiguration {

    @Bean
    OrderOutboxPersistenceAdapter orderOutboxPersistenceAdapter(JdbcTemplate jdbc) {
        return new OrderOutboxPersistenceAdapter(jdbc);
    }

    @Bean
    OrderOutboxRetryPolicy orderOutboxRetryPolicy(OrderOutboxProperties properties) {
        return new OrderOutboxRetryPolicy(properties.retryBackoffCap());
    }

    @Bean
    OrderCreatedAvroMapper orderCreatedAvroMapper(ObjectMapper objectMapper) {
        return new OrderCreatedAvroMapper(objectMapper);
    }

    @Bean
    KafkaOrderCreatedPublisher kafkaOrderCreatedPublisher(
            KafkaTemplate<String, OrderCreatedV1> kafka, OrderCreatedAvroMapper mapper,
            OrderKafkaProperties properties) {
        return new KafkaOrderCreatedPublisher(kafka, mapper, properties);
    }

    @Bean
    PaymentRequestedAvroMapper paymentRequestedAvroMapper(ObjectMapper objectMapper) {
        return new PaymentRequestedAvroMapper(objectMapper);
    }

    @Bean
    KafkaPaymentRequestedPublisher kafkaPaymentRequestedPublisher(
            KafkaTemplate<String, PaymentRequestedV1> kafka, PaymentRequestedAvroMapper mapper,
            OrderKafkaProperties properties) {
        return new KafkaPaymentRequestedPublisher(kafka, mapper, properties);
    }

    @Bean
    ConfirmReservationAvroMapper confirmReservationAvroMapper(ObjectMapper objectMapper) {
        return new ConfirmReservationAvroMapper(objectMapper);
    }

    @Bean
    KafkaConfirmReservationPublisher kafkaConfirmReservationPublisher(
            KafkaTemplate<String, ConfirmPurchaseReservationV1> kafka,
            ConfirmReservationAvroMapper mapper, OrderKafkaProperties properties) {
        return new KafkaConfirmReservationPublisher(kafka, mapper, properties);
    }

    @Bean
    OrderOutboxEventTypeDispatcher orderOutboxEventTypeDispatcher(
            KafkaOrderCreatedPublisher orderCreatedPublisher,
            KafkaPaymentRequestedPublisher paymentRequestedPublisher,
            KafkaConfirmReservationPublisher confirmReservationPublisher) {
        return new OrderOutboxEventTypeDispatcher(Map.of(
                "OrderCreated", orderCreatedPublisher,
                "PaymentRequested", paymentRequestedPublisher,
                "ConfirmPurchaseReservation", confirmReservationPublisher));
    }

    @Bean
    OrderOutboxPublicationService orderOutboxPublicationService(
            ClaimOrderOutboxEventsPort claims, OrderOutboxEventTypeDispatcher publisher,
            UpdateOrderOutboxPublicationPort updates, OrderOutboxRetryPolicy retryPolicy,
            OrderOutboxProperties properties, OrderObservability observability) {
        return new OrderOutboxPublicationService(claims, publisher, updates, retryPolicy,
                properties.batchSize(), properties.claimLease(), observability);
    }

    @Bean
    OrderOutboxPublisherJob orderOutboxPublisherJob(OrderOutboxPublicationService publication) {
        return new OrderOutboxPublisherJob(publication);
    }
}
