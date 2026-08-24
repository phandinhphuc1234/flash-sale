package com.philia.flashsale.order.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v1.OrderCreatedV1;
import com.philia.flashsale.order.outbox.adapter.in.scheduling.OrderOutboxPublisherJob;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.KafkaOrderCreatedPublisher;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderCreatedAvroMapper;
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
    OrderOutboxEventTypeDispatcher orderOutboxEventTypeDispatcher(
            KafkaOrderCreatedPublisher publisher) {
        // G2 registers only the already-live OrderCreated publisher. Saga
        // publishers are added by their owning story without changing retry/lease semantics.
        return new OrderOutboxEventTypeDispatcher(Map.of("OrderCreated", publisher));
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
