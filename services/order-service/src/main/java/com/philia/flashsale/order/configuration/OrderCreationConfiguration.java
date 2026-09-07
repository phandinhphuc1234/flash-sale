package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.OrderCreationJpaAdapter;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderConsumerInboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderCreationOutboxJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderLineJpaRepository;
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
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.PaymentFailurePersistenceAdapter;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyPaymentSuccessService;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentFailureUseCase;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyPaymentFailureService;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.ReservationConfirmationPersistenceAdapter;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.ReservationReleasePersistenceAdapter;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationReleaseUseCase;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyPurchaseReservationConfirmationService;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyPurchaseReservationReleaseService;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.RegularHoldConfirmationPersistenceAdapter;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.repository.RegularPurchaseRequestJpaRepository;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyRegularHoldConfirmationService;
import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.RegularHoldRecoveryPersistenceAdapter;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldOutcomeUseCase;
import com.philia.flashsale.order.purchasesaga.application.usecase.ApplyRegularHoldOutcomeService;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            PurchaseSagaJpaRepository sagas, OrderJpaRepository orders,
            PurchaseSagaInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox) {
        return new PaymentSuccessPersistenceAdapter(sagas, orders, inbox, outbox);
    }

    @Bean
    public ApplyPaymentSuccessUseCase applyPaymentSuccessUseCase(PaymentSuccessPersistenceAdapter persistence) {
        return new ApplyPaymentSuccessService(persistence);
    }

    @Bean
    public PaymentFailurePersistenceAdapter paymentFailurePersistenceAdapter(OrderJpaRepository orders,
            PurchaseSagaJpaRepository sagas, PurchaseSagaInboxJpaRepository inbox,
            OrderCreationOutboxJpaRepository outbox) {
        return new PaymentFailurePersistenceAdapter(orders, sagas, inbox, outbox);
    }

    @Bean
    public ApplyPaymentFailureUseCase applyPaymentFailureUseCase(PaymentFailurePersistenceAdapter persistence) {
        return new ApplyPaymentFailureService(persistence);
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

    @Bean
    public RegularHoldConfirmationPersistenceAdapter regularHoldConfirmationPersistenceAdapter(
            OrderJpaRepository orders, OrderLineJpaRepository lines, PurchaseSagaJpaRepository sagas,
            PurchaseSagaInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox,
            RegularPurchaseRequestJpaRepository regularRequests, ObjectMapper objectMapper,
            @Value("${order.regular-purchase.runtime.cart-reconciliation-producer-enabled:false}")
            boolean cartReconciliationEnabled) {
        return new RegularHoldConfirmationPersistenceAdapter(orders, lines, sagas, inbox, outbox,
                regularRequests, objectMapper, cartReconciliationEnabled);
    }

    @Bean
    public ApplyRegularHoldConfirmationUseCase applyRegularHoldConfirmationUseCase(
            RegularHoldConfirmationPersistenceAdapter persistence) {
        return new ApplyRegularHoldConfirmationService(persistence);
    }

    @Bean
    public RegularHoldRecoveryPersistenceAdapter regularHoldRecoveryPersistenceAdapter(
            OrderJpaRepository orders, OrderLineJpaRepository lines, PurchaseSagaJpaRepository sagas,
            PurchaseSagaInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox) {
        return new RegularHoldRecoveryPersistenceAdapter(orders, lines, sagas, inbox, outbox);
    }

    @Bean
    public ApplyRegularHoldOutcomeUseCase applyRegularHoldOutcomeUseCase(
            RegularHoldRecoveryPersistenceAdapter persistence) {
        return new ApplyRegularHoldOutcomeService(persistence);
    }

    @Bean
    public ReservationReleasePersistenceAdapter reservationReleasePersistenceAdapter(
            OrderJpaRepository orders, PurchaseSagaJpaRepository sagas,
            PurchaseSagaInboxJpaRepository inbox, OrderCreationOutboxJpaRepository outbox) {
        return new ReservationReleasePersistenceAdapter(orders, sagas, inbox, outbox);
    }

    @Bean
    public ApplyPurchaseReservationReleaseUseCase applyPurchaseReservationReleaseUseCase(
            ReservationReleasePersistenceAdapter persistence) {
        return new ApplyPurchaseReservationReleaseService(persistence);
    }
}
