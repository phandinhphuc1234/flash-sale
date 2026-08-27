package com.philia.flashsale.order.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderCreatedV1;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1;
import com.philia.flashsale.contract.order.event.v1.OrderConfirmedV1;
import com.philia.flashsale.contract.order.event.v1.OrderCancelledV1;
import com.philia.flashsale.contract.order.event.v1.OrderExpiredV1;
import com.philia.flashsale.contract.order.event.v1.OrderPaymentReviewRequiredV1;
import com.philia.flashsale.order.outbox.adapter.in.scheduling.OrderOutboxPublisherJob;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaOrderCreatedPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderCreatedAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaPaymentRequestedPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.PaymentRequestedAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.ConfirmReservationAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaConfirmReservationPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaReleaseReservationPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.ReleaseReservationAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderConfirmedAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaOrderConfirmedPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaOrderCancelledPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaOrderExpiredPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderCancelledAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderExpiredAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderPaymentReviewRequiredAvroMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaOrderPaymentReviewRequiredPublisher;
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
    ReleaseReservationAvroMapper releaseReservationAvroMapper(ObjectMapper objectMapper) {
        return new ReleaseReservationAvroMapper(objectMapper);
    }

    @Bean
    KafkaReleaseReservationPublisher kafkaReleaseReservationPublisher(
            KafkaTemplate<String, ReleasePurchaseReservationV1> kafka,
            ReleaseReservationAvroMapper mapper, OrderKafkaProperties properties) {
        return new KafkaReleaseReservationPublisher(kafka, mapper, properties);
    }

    @Bean
    OrderConfirmedAvroMapper orderConfirmedAvroMapper(ObjectMapper objectMapper) {
        return new OrderConfirmedAvroMapper(objectMapper);
    }

    @Bean
    KafkaOrderConfirmedPublisher kafkaOrderConfirmedPublisher(
            KafkaTemplate<String, OrderConfirmedV1> kafka, OrderConfirmedAvroMapper mapper,
            OrderKafkaProperties properties) {
        return new KafkaOrderConfirmedPublisher(kafka, mapper, properties);
    }

    @Bean
    OrderCancelledAvroMapper orderCancelledAvroMapper(ObjectMapper objectMapper) {
        return new OrderCancelledAvroMapper(objectMapper);
    }

    @Bean
    KafkaOrderCancelledPublisher kafkaOrderCancelledPublisher(
            KafkaTemplate<String, OrderCancelledV1> kafka, OrderCancelledAvroMapper mapper,
            OrderKafkaProperties properties) {
        return new KafkaOrderCancelledPublisher(kafka, mapper, properties);
    }

    @Bean
    OrderExpiredAvroMapper orderExpiredAvroMapper(ObjectMapper objectMapper) {
        return new OrderExpiredAvroMapper(objectMapper);
    }

    @Bean
    KafkaOrderExpiredPublisher kafkaOrderExpiredPublisher(
            KafkaTemplate<String, OrderExpiredV1> kafka, OrderExpiredAvroMapper mapper,
            OrderKafkaProperties properties) {
        return new KafkaOrderExpiredPublisher(kafka, mapper, properties);
    }

    @Bean
    OrderPaymentReviewRequiredAvroMapper orderPaymentReviewRequiredAvroMapper(ObjectMapper objectMapper) {
        return new OrderPaymentReviewRequiredAvroMapper(objectMapper);
    }

    @Bean
    KafkaOrderPaymentReviewRequiredPublisher kafkaOrderPaymentReviewRequiredPublisher(
            KafkaTemplate<String, OrderPaymentReviewRequiredV1> kafka,
            OrderPaymentReviewRequiredAvroMapper mapper, OrderKafkaProperties properties) {
        return new KafkaOrderPaymentReviewRequiredPublisher(kafka, mapper, properties);
    }

    @Bean
    OrderOutboxEventTypeDispatcher orderOutboxEventTypeDispatcher(
            KafkaOrderCreatedPublisher orderCreatedPublisher,
            KafkaPaymentRequestedPublisher paymentRequestedPublisher,
            KafkaConfirmReservationPublisher confirmReservationPublisher,
            KafkaOrderConfirmedPublisher orderConfirmedPublisher,
            KafkaReleaseReservationPublisher releaseReservationPublisher,
            KafkaOrderCancelledPublisher orderCancelledPublisher,
            KafkaOrderExpiredPublisher orderExpiredPublisher,
            KafkaOrderPaymentReviewRequiredPublisher reviewPublisher) {
        return new OrderOutboxEventTypeDispatcher(Map.of(
                "OrderCreated", orderCreatedPublisher,
                "PaymentRequested", paymentRequestedPublisher,
                "ConfirmPurchaseReservation", confirmReservationPublisher,
                "OrderConfirmed", orderConfirmedPublisher,
                "ReleasePurchaseReservation", releaseReservationPublisher,
                "OrderCancelled", orderCancelledPublisher,
                "OrderExpired", orderExpiredPublisher,
                "OrderPaymentReviewRequired", reviewPublisher));
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
