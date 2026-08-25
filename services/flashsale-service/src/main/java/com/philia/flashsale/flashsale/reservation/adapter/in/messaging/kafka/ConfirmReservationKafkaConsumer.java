package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.flashsale.reservation.application.port.in.ConfirmReservationUseCase;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Acknowledges a confirm command only after PostgreSQL and Redis have accepted it. */
@Component
@ConditionalOnProperty(name = "flashsale.reservation-commands.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class ConfirmReservationKafkaConsumer {
    private final ConfirmReservationAvroMapper mapper;
    private final ConfirmReservationUseCase useCase;

    public ConfirmReservationKafkaConsumer(ConfirmReservationAvroMapper mapper,
            ConfirmReservationUseCase useCase) {
        this.mapper = mapper;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${flashsale.reservation-commands.topic}",
            groupId = "${flashsale.reservation-commands.consumer-group}",
            containerFactory = "flashSaleReservationCommandKafkaListenerContainerFactory",
            autoStartup = "${flashsale.reservation-commands.enabled:true}")
    public void onMessage(ConsumerRecord<String, ConfirmPurchaseReservationV1> record,
            Acknowledgment acknowledgment) {
        useCase.confirm(mapper.map(record));
        acknowledgment.acknowledge();
    }

    static String header(ConsumerRecord<String, ConfirmPurchaseReservationV1> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
