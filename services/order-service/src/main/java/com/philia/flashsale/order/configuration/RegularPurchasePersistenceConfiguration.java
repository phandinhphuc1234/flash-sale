package com.philia.flashsale.order.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.RegularPurchasePersistenceAdapter;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.repository.RegularPurchaseRequestJpaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the Order-owned regular purchase persistence capability while intake remains runtime-disabled. */
@Configuration
@ConditionalOnProperty(name = "order.creation.enabled", havingValue = "true", matchIfMissing = true)
public class RegularPurchasePersistenceConfiguration {

    @Bean
    public RegularPurchasePersistenceAdapter regularPurchasePersistenceAdapter(
            RegularPurchaseRequestJpaRepository requests, OrderJpaRepository orders,
            PurchaseSagaJpaRepository sagas, OrderCreationOutboxJpaRepository outbox,
            EntityManager entityManager, ObjectMapper objectMapper) {
        return new RegularPurchasePersistenceAdapter(requests, orders, sagas, outbox, entityManager, objectMapper);
    }
}
