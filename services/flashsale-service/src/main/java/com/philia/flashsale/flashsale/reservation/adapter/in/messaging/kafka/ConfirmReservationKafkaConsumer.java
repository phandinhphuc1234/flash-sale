package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.purchase.command.v1.ConfirmPurchaseReservationV1;
import com.philia.flashsale.contract.purchase.command.v1.ReleasePurchaseReservationV1;
import com.philia.flashsale.flashsale.reservation.application.port.in.ConfirmReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.in.ReleaseReservationUseCase;
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
    private final ReleaseReservationAvroMapper releaseMapper;
    private final ReleaseReservationUseCase releaseUseCase;

    public ConfirmReservationKafkaConsumer(ConfirmReservationAvroMapper mapper,
            ConfirmReservationUseCase useCase, ReleaseReservationAvroMapper releaseMapper,
            ReleaseReservationUseCase releaseUseCase) {
        this.mapper = mapper;
        this.useCase = useCase;
        this.releaseMapper = releaseMapper;
        this.releaseUseCase = releaseUseCase;
    }

    @KafkaListener(topics = "${flashsale.reservation-commands.topic}",
            groupId = "${flashsale.reservation-commands.consumer-group}",
            containerFactory = "flashSaleReservationCommandKafkaListenerContainerFactory",
            autoStartup = "${flashsale.reservation-commands.enabled:true}")
    public void onMessage(ConsumerRecord<String, Object> record,
            Acknowledgment acknowledgment) {
        if (record.value() instanceof ConfirmPurchaseReservationV1) {
            @SuppressWarnings("unchecked") ConsumerRecord<String, ConfirmPurchaseReservationV1> confirmRecord =
                    (ConsumerRecord<String, ConfirmPurchaseReservationV1>) (ConsumerRecord<?, ?>) record;
            useCase.confirm(mapper.map(confirmRecord));
        } else if (record.value() instanceof ReleasePurchaseReservationV1) {
            @SuppressWarnings("unchecked") ConsumerRecord<String, ReleasePurchaseReservationV1> releaseRecord =
                    (ConsumerRecord<String, ReleasePurchaseReservationV1>) (ConsumerRecord<?, ?>) record;
            releaseUseCase.release(releaseMapper.map(releaseRecord));
        } else {
            throw new ReleaseReservationRecordException("unsupported reservation command SpecificRecord");
        }
        acknowledgment.acknowledge();
    }

    static String header(ConsumerRecord<String, ConfirmPurchaseReservationV1> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
