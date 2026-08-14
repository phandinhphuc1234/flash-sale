package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.outbox.adapter.in.scheduling.FlashSaleOutboxPublisherJob;
import com.philia.flashsale.flashsale.outbox.adapter.out.persistence.jpa.FlashSaleOutboxPersistenceAdapter;
import com.philia.flashsale.flashsale.outbox.application.port.ClaimOutboxEventsPort;
import com.philia.flashsale.flashsale.outbox.application.port.PublishPurchaseAcceptedPort;
import com.philia.flashsale.flashsale.outbox.application.port.UpdateOutboxPublicationPort;
import com.philia.flashsale.flashsale.outbox.application.usecase.OutboxPublicationService;
import com.philia.flashsale.flashsale.outbox.application.usecase.OutboxRetryPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires the outbox relay only when durable storage and Kafka publication are available. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class FlashSaleOutboxConfiguration {

    @Bean
    FlashSaleOutboxPersistenceAdapter flashSaleOutboxPersistenceAdapter(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new FlashSaleOutboxPersistenceAdapter(jdbcTemplate, objectMapper);
    }

    @Bean
    OutboxRetryPolicy outboxRetryPolicy(OutboxProperties properties) {
        return new OutboxRetryPolicy(properties.retryBackoffCap());
    }

    @Bean
    @ConditionalOnBean({ClaimOutboxEventsPort.class, PublishPurchaseAcceptedPort.class,
            UpdateOutboxPublicationPort.class})
    OutboxPublicationService outboxPublicationService(ClaimOutboxEventsPort claims,
            PublishPurchaseAcceptedPort publisher, UpdateOutboxPublicationPort updates,
            OutboxRetryPolicy retryPolicy, OutboxProperties properties) {
        return new OutboxPublicationService(claims, publisher, updates, retryPolicy,
                properties.batchSize(), properties.claimLease());
    }

    @Bean
    @ConditionalOnBean(OutboxPublicationService.class)
    FlashSaleOutboxPublisherJob flashSaleOutboxPublisherJob(OutboxPublicationService publication) {
        return new FlashSaleOutboxPublisherJob(publication);
    }
}
