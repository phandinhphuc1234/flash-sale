package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.OrderCreationJpaAdapter;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderConsumerInboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.application.usecase.AcceptedPurchaseFingerprintService;
import com.philia.flashsale.order.order.application.usecase.CreateOrderFromAcceptedPurchaseService;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the atomic Order creation use case only when JPA infrastructure is active. */
@Configuration
@ConditionalOnProperty(name = "order.creation.enabled", havingValue = "true", matchIfMissing = true)
public class OrderCreationConfiguration {

    @Bean
    public AcceptedPurchaseFingerprintService acceptedPurchaseFingerprintService() {
        return new AcceptedPurchaseFingerprintService();
    }

    @Bean
    public OrderCreationJpaAdapter orderCreationJpaAdapter(OrderJpaRepository orders,
            OrderConsumerInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox,
            EntityManager entityManager) {
        return new OrderCreationJpaAdapter(orders, inbox, outbox, entityManager);
    }

    @Bean
    public CreateOrderFromAcceptedPurchaseUseCase createOrderFromAcceptedPurchaseUseCase(
            OrderCreationJpaAdapter persistence,
            @Qualifier("generateOrderIdentityPort") GenerateOrderIdentityPort identities,
            @Qualifier("generateOrderNumberPort") GenerateOrderNumberPort orderNumbers,
            @Qualifier("currentTimePort") CurrentTimePort clock,
            AcceptedPurchaseFingerprintService fingerprints) {
        return new CreateOrderFromAcceptedPurchaseService(
                persistence, identities, orderNumbers, clock, fingerprints);
    }
}
