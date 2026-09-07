package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.regularhold.event.v1.RegularStockHoldConfirmedV1;
import com.philia.flashsale.order.observability.OrderObservability;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldOutcomeUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldConfirmationResult;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldOutcomeResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Acknowledges Inventory regular-hold facts only after the local Order/Saga/outbox transaction commits. */
@Component
@ConditionalOnProperty(name = {"order.creation.enabled", "order.regular-purchase.runtime.hold-result-consumer-enabled"},
        havingValue = "true")
public final class RegularHoldResultKafkaConsumer {
    private final RegularStockHoldConfirmedAvroMapper mapper;
    private final ApplyRegularHoldConfirmationUseCase useCase;
    private final RegularStockHoldOutcomeAvroMapper outcomeMapper;
    private final ApplyRegularHoldOutcomeUseCase outcomeUseCase;
    private final OrderObservability observability;

    /** Compatibility constructor for the original confirmation-only boundary. */
    public RegularHoldResultKafkaConsumer(RegularStockHoldConfirmedAvroMapper mapper,
            ApplyRegularHoldConfirmationUseCase useCase, OrderObservability observability) {
        this(mapper, useCase, null, null, observability);
    }

    @Autowired
    public RegularHoldResultKafkaConsumer(RegularStockHoldConfirmedAvroMapper mapper,
            ApplyRegularHoldConfirmationUseCase useCase, RegularStockHoldOutcomeAvroMapper outcomeMapper,
            ApplyRegularHoldOutcomeUseCase outcomeUseCase, OrderObservability observability) {
        this.mapper = mapper;
        this.useCase = useCase;
        this.outcomeMapper = outcomeMapper;
        this.outcomeUseCase = outcomeUseCase;
        this.observability = observability;
    }

    @KafkaListener(topics = "${order.kafka.regular-hold-events-topic}",
            groupId = "${order.kafka.regular-hold-events-consumer-group}",
            containerFactory = "orderRegularHoldResultKafkaListenerContainerFactory",
            autoStartup = "${order.regular-purchase.runtime.hold-result-consumer-enabled:false}")
    public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        if (!(record.value() instanceof RegularStockHoldConfirmedV1)) {
            if (outcomeMapper == null || outcomeUseCase == null) {
                throw new RegularStockHoldConfirmedRecordException("unsupported regular-hold result SpecificRecord");
            }
            RegularHoldOutcomeResult result = outcomeUseCase.apply(outcomeMapper.map(record));
            observability.recordConsumerOutcome(OrderObservability.ConsumerBoundary.REGULAR_HOLD_RESULTS,
                    result.outcome().name());
            if (result.outcome() == RegularHoldOutcomeResult.Outcome.CONFLICT) {
                throw new RegularStockHoldOutcomeConflictException(result.conflictReason());
            }
            acknowledgment.acknowledge();
            return;
        }
        @SuppressWarnings("unchecked") ConsumerRecord<String, RegularStockHoldConfirmedV1> confirmed =
                (ConsumerRecord<String, RegularStockHoldConfirmedV1>) (ConsumerRecord<?, ?>) record;
        RegularHoldConfirmationResult result = useCase.apply(mapper.map(confirmed));
        observability.recordConsumerOutcome(OrderObservability.ConsumerBoundary.REGULAR_HOLD_RESULTS,
                result.outcome().name());
        if (result.outcome() == RegularHoldConfirmationResult.Outcome.CONFLICT) {
            throw new RegularStockHoldConfirmedConflictException(result.conflictReason());
        }
        acknowledgment.acknowledge();
    }
}
