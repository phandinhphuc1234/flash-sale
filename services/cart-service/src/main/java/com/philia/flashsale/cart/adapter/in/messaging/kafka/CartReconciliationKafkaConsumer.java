package com.philia.flashsale.cart.adapter.in.messaging.kafka;

import com.philia.flashsale.cart.application.port.in.ReconcilePurchasedCartSnapshotUseCase;
import com.philia.flashsale.contract.cart.command.v1.ReconcilePurchasedCartSnapshotV1;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Acknowledges only after the Cart conditional deletes and inbox receipt commit. */
@Component
@ConditionalOnProperty(name = "cart.checkout.reconciliation.consumer-enabled", havingValue = "true")
public final class CartReconciliationKafkaConsumer {
    private final ReconcilePurchasedCartSnapshotAvroMapper mapper;
    private final ReconcilePurchasedCartSnapshotUseCase useCase;

    public CartReconciliationKafkaConsumer(ReconcilePurchasedCartSnapshotAvroMapper mapper,
            ReconcilePurchasedCartSnapshotUseCase useCase) {
        this.mapper = mapper;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${cart.checkout.reconciliation.topic}",
            groupId = "${cart.checkout.reconciliation.consumer-group}",
            containerFactory = "cartReconciliationKafkaListenerContainerFactory",
            autoStartup = "${cart.checkout.reconciliation.consumer-enabled:false}")
    public void onMessage(ConsumerRecord<String, ReconcilePurchasedCartSnapshotV1> record,
            Acknowledgment acknowledgment) {
        useCase.reconcile(mapper.map(record));
        acknowledgment.acknowledge();
    }
}
