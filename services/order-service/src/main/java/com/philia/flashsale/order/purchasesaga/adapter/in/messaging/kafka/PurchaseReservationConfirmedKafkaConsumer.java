package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1;
import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationReleasedV1;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationReleaseUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationConfirmationResult;
import com.philia.flashsale.order.observability.OrderObservability;
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
    private final PurchaseReservationReleasedAvroMapper releaseMapper;
    private final ApplyPurchaseReservationReleaseUseCase releaseUseCase;
    private final OrderObservability observability;

    public PurchaseReservationConfirmedKafkaConsumer(PurchaseReservationConfirmedAvroMapper mapper,
            ApplyPurchaseReservationConfirmationUseCase useCase,
            PurchaseReservationReleasedAvroMapper releaseMapper,
            ApplyPurchaseReservationReleaseUseCase releaseUseCase,
            OrderObservability observability) {
        this.mapper = mapper;
        this.useCase = useCase;
        this.releaseMapper = releaseMapper;
        this.releaseUseCase = releaseUseCase;
        this.observability = observability;
    }

    @KafkaListener(topics = "${order.kafka.purchase-reservation-results-topic}",
            groupId = "${order.kafka.purchase-reservation-results-consumer-group}",
            containerFactory = "orderPurchaseReservationConfirmedKafkaListenerContainerFactory",
            autoStartup = "${order.runtime.purchase-reservation-results-consumer-enabled:true}")
    public void onMessage(ConsumerRecord<String, Object> record,
            Acknowledgment acknowledgment) {
        if (record.value() instanceof PurchaseReservationConfirmedV1) {
            @SuppressWarnings("unchecked") ConsumerRecord<String, PurchaseReservationConfirmedV1> confirmed =
                    (ConsumerRecord<String, PurchaseReservationConfirmedV1>) (ConsumerRecord<?, ?>) record;
            PurchaseReservationConfirmationResult result = useCase.apply(mapper.map(confirmed));
            observability.recordConsumerOutcome(OrderObservability.ConsumerBoundary.RESERVATION_RESULTS,
                    result.outcome().name());
            if (result.outcome() == PurchaseReservationConfirmationResult.Outcome.CONFLICT) {
                throw new PurchaseReservationConfirmedConflictException(result.conflictReason());
            }
        } else if (record.value() instanceof PurchaseReservationReleasedV1) {
            @SuppressWarnings("unchecked") ConsumerRecord<String, PurchaseReservationReleasedV1> released =
                    (ConsumerRecord<String, PurchaseReservationReleasedV1>) (ConsumerRecord<?, ?>) record;
            var result = releaseUseCase.apply(releaseMapper.map(released));
            observability.recordConsumerOutcome(OrderObservability.ConsumerBoundary.RESERVATION_RESULTS,
                    result.outcome().name());
            if (result.outcome() == com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationReleaseResult.Outcome.CONFLICT) {
                throw new PurchaseReservationReleasedConflictException(result.conflictReason());
            }
        } else {
            throw new PurchaseReservationReleasedRecordException("unsupported reservation result SpecificRecord");
        }
        acknowledgment.acknowledge();
    }
}
