package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;
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

    public PaymentSucceededKafkaConsumer(PaymentSucceededAvroMapper mapper,
            ApplyPaymentSuccessUseCase useCase) {
        this.mapper = mapper;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${order.kafka.payment-events-topic}",
            groupId = "${order.kafka.payment-events-consumer-group}",
            containerFactory = "orderPaymentSucceededKafkaListenerContainerFactory",
            autoStartup = "${order.runtime.payment-events-consumer-enabled:true}")
    public void onMessage(ConsumerRecord<String, PaymentSucceededV1> record, Acknowledgment acknowledgment) {
        PaymentSuccessResult result = useCase.apply(mapper.map(record));
        if (result.outcome() == PaymentSuccessResult.Outcome.CONFLICT) {
            throw new PaymentSucceededConflictException(result.conflictReason());
        }
        acknowledgment.acknowledge();
    }

    static String header(ConsumerRecord<String, PaymentSucceededV1> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
