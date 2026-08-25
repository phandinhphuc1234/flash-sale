package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationConfirmationResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Acknowledges reservation confirmations only after the Order transaction commits. */
@Component
@ConditionalOnProperty(name = {"order.creation.enabled", "order.runtime.purchase-reservation-results-consumer-enabled"},
        havingValue = "true", matchIfMissing = true)
public final class PurchaseReservationConfirmedKafkaConsumer {
    private final PurchaseReservationConfirmedAvroMapper mapper;
    private final ApplyPurchaseReservationConfirmationUseCase useCase;

    public PurchaseReservationConfirmedKafkaConsumer(PurchaseReservationConfirmedAvroMapper mapper,
            ApplyPurchaseReservationConfirmationUseCase useCase) {
        this.mapper = mapper;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${order.kafka.purchase-reservation-results-topic}",
            groupId = "${order.kafka.purchase-reservation-results-consumer-group}",
            containerFactory = "orderPurchaseReservationConfirmedKafkaListenerContainerFactory",
            autoStartup = "${order.runtime.purchase-reservation-results-consumer-enabled:true}")
    public void onMessage(ConsumerRecord<String, PurchaseReservationConfirmedV1> record,
            Acknowledgment acknowledgment) {
        PurchaseReservationConfirmationResult result = useCase.apply(mapper.map(record));
        if (result.outcome() == PurchaseReservationConfirmationResult.Outcome.CONFLICT) {
            throw new PurchaseReservationConfirmedConflictException(result.conflictReason());
        }
        acknowledgment.acknowledge();
    }
}
