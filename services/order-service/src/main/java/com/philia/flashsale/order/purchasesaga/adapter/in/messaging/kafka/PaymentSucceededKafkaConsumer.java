package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentFailureUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentFailureResult;
import com.philia.flashsale.order.observability.OrderObservability;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Inbound adapter that acknowledges a PaymentSucceeded fact after its Saga transaction commits. */
@Component
@ConditionalOnProperty(name = {"order.creation.enabled", "order.runtime.payment-events-consumer-enabled"},
        havingValue = "true", matchIfMissing = true)
public final class PaymentSucceededKafkaConsumer {
    private final PaymentSucceededAvroMapper mapper;
    private final ApplyPaymentSuccessUseCase useCase;
    private final PaymentFailedAvroMapper failureMapper;
    private final ApplyPaymentFailureUseCase failureUseCase;
    private final OrderObservability observability;

    @Autowired
    public PaymentSucceededKafkaConsumer(PaymentSucceededAvroMapper mapper,
            ApplyPaymentSuccessUseCase useCase, PaymentFailedAvroMapper failureMapper,
            ApplyPaymentFailureUseCase failureUseCase, OrderObservability observability) {
        this.mapper = mapper;
        this.useCase = useCase;
        this.failureMapper = failureMapper;
        this.failureUseCase = failureUseCase;
        this.observability = observability;
    }

    @KafkaListener(topics = "${order.kafka.payment-events-topic}",
            groupId = "${order.kafka.payment-events-consumer-group}",
            containerFactory = "orderPaymentSucceededKafkaListenerContainerFactory",
            autoStartup = "${order.runtime.payment-events-consumer-enabled:true}")
    public void onMessage(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        Object value = record.value();
        if (value instanceof PaymentSucceededV1) {
            @SuppressWarnings("unchecked") ConsumerRecord<String, PaymentSucceededV1> successRecord =
                    (ConsumerRecord<String, PaymentSucceededV1>) (ConsumerRecord<?, ?>) record;
            PaymentSuccessResult result = useCase.apply(mapper.map(successRecord));
            observability.recordConsumerOutcome(OrderObservability.ConsumerBoundary.PAYMENT_RESULTS,
                    result.outcome().name());
            if (result.outcome() == PaymentSuccessResult.Outcome.CONFLICT) {
                throw new PaymentSucceededConflictException(result.conflictReason());
            }
        } else if (value instanceof PaymentFailedV1) {
            @SuppressWarnings("unchecked") ConsumerRecord<String, PaymentFailedV1> failureRecord =
                    (ConsumerRecord<String, PaymentFailedV1>) (ConsumerRecord<?, ?>) record;
            PaymentFailureResult result = failureUseCase.apply(failureMapper.map(failureRecord));
            observability.recordConsumerOutcome(OrderObservability.ConsumerBoundary.PAYMENT_RESULTS,
                    result.outcome().name());
            if (result.outcome() == PaymentFailureResult.Outcome.CONFLICT) {
                throw new PaymentFailedConflictException(result.conflictReason());
            }
        } else {
            throw new PaymentFailedRecordException("unsupported payment event SpecificRecord");
        }
        acknowledgment.acknowledge();
    }

    static String header(ConsumerRecord<String, PaymentSucceededV1> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
