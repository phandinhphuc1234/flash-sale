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
import com.philia.flashsale.order.observability.OrderObservability;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository.PurchaseSagaInboxJpaRepository;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.PaymentSuccessPersistenceAdapter;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyPaymentSuccessService;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.ReservationConfirmationPersistenceAdapter;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyPurchaseReservationConfirmationService;
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
            PurchaseSagaJpaRepository purchaseSagas, EntityManager entityManager,
            OrderObservability observability) {
        return new OrderCreationJpaAdapter(orders, inbox, outbox, entityManager, purchaseSagas, observability);
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

    @Bean
    public PaymentSuccessPersistenceAdapter paymentSuccessPersistenceAdapter(
            PurchaseSagaJpaRepository sagas, PurchaseSagaInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox) {
        return new PaymentSuccessPersistenceAdapter(sagas, inbox, outbox);
    }

    @Bean
    public ApplyPaymentSuccessUseCase applyPaymentSuccessUseCase(PaymentSuccessPersistenceAdapter persistence) {
        return new ApplyPaymentSuccessService(persistence);
    }

    @Bean
    public ReservationConfirmationPersistenceAdapter reservationConfirmationPersistenceAdapter(
            OrderJpaRepository orders, PurchaseSagaJpaRepository sagas,
            PurchaseSagaInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox) {
        return new ReservationConfirmationPersistenceAdapter(orders, sagas, inbox, outbox);
    }

    @Bean
    public ApplyPurchaseReservationConfirmationUseCase applyPurchaseReservationConfirmationUseCase(
            ReservationConfirmationPersistenceAdapter persistence) {
        return new ApplyPurchaseReservationConfirmationService(persistence);
    }
}
