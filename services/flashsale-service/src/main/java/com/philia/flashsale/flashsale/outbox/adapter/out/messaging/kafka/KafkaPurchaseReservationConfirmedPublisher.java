package com.philia.flashsale.flashsale.outbox.adapter.out.messaging.kafka;

import com.philia.flashsale.contract.purchase.event.v1.PurchaseReservationConfirmedV1;
import com.philia.flashsale.flashsale.configuration.OutboxProperties;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import com.philia.flashsale.flashsale.outbox.application.port.PublishPurchaseReservationConfirmedPort;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes confirmed reservation facts keyed by Order identity. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class KafkaPurchaseReservationConfirmedPublisher
        implements PublishPurchaseReservationConfirmedPort {
    private final KafkaTemplate<String, PurchaseReservationConfirmedV1> kafka;
    private final PurchaseReservationConfirmedAvroMapper mapper;
    private final OutboxProperties properties;
    private final FlashSaleObservability observability;

    public KafkaPurchaseReservationConfirmedPublisher(KafkaTemplate<String, PurchaseReservationConfirmedV1> kafka,
            PurchaseReservationConfirmedAvroMapper mapper, OutboxProperties properties) {
        this(kafka, mapper, properties, FlashSaleObservability.noop());
    }

    @Autowired
    public KafkaPurchaseReservationConfirmedPublisher(KafkaTemplate<String, PurchaseReservationConfirmedV1> kafka,
            PurchaseReservationConfirmedAvroMapper mapper, OutboxProperties properties,
            FlashSaleObservability observability) {
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    @Override
    public void publish(OutboxEvent event) {
        observability.observe(FlashSaleObservability.Operation.OUTBOX_PUBLICATION, () -> {
            var value = mapper.map(event);
            var record = new ProducerRecord<String, PurchaseReservationConfirmedV1>(
                    properties.topic(), value.getData().getOrderId().toString(), value);
            header(record, "traceparent", event.payload().get("traceparent"));
            header(record, "tracestate", event.payload().get("tracestate"));
            try {
                kafka.send(record).get(30, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("confirmed outcome publication interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("confirmed outcome publication failed", exception);
            }
        });
    }

    private void header(ProducerRecord<?, ?> record, String name, Object value) {
        if (value != null && !value.toString().isBlank()) {
            record.headers().add(name, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
