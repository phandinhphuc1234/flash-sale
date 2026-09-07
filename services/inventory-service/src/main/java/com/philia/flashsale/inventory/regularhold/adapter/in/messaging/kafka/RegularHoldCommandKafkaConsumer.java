package com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1;
import com.philia.flashsale.contract.regularhold.command.v1.ReleaseRegularStockHoldV1;
import com.philia.flashsale.inventory.regularhold.application.exception.RegularHoldCommandConflictException;
import com.philia.flashsale.inventory.regularhold.application.port.in.ProcessConfirmRegularHoldCommandUseCase;
import com.philia.flashsale.inventory.regularhold.application.port.in.ProcessReleaseRegularHoldCommandUseCase;
import com.philia.flashsale.inventory.observability.InventoryObservability;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Acknowledges only after the local inbox, stock result, and durable outbox transaction commits. */
@Component
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.command-consumer-enabled", havingValue = "true")
public class RegularHoldCommandKafkaConsumer {
    private final ConfirmRegularHoldAvroMapper confirmMapper;
    private final ReleaseRegularHoldAvroMapper releaseMapper;
    private final ProcessConfirmRegularHoldCommandUseCase confirmProcessor;
    private final ProcessReleaseRegularHoldCommandUseCase releaseProcessor;
    private final InventoryObservability observability;

    /** Backward-compatible constructor for mapper/consumer unit tests. */
    public RegularHoldCommandKafkaConsumer(ConfirmRegularHoldAvroMapper confirmMapper,
            ReleaseRegularHoldAvroMapper releaseMapper, ProcessConfirmRegularHoldCommandUseCase confirmProcessor,
            ProcessReleaseRegularHoldCommandUseCase releaseProcessor) {
        this(confirmMapper, releaseMapper, confirmProcessor, releaseProcessor, InventoryObservability.noop());
    }

    @Autowired
    public RegularHoldCommandKafkaConsumer(ConfirmRegularHoldAvroMapper confirmMapper,
            ReleaseRegularHoldAvroMapper releaseMapper, ProcessConfirmRegularHoldCommandUseCase confirmProcessor,
            ProcessReleaseRegularHoldCommandUseCase releaseProcessor, InventoryObservability observability) {
        this.confirmMapper = confirmMapper;
        this.releaseMapper = releaseMapper;
        this.confirmProcessor = confirmProcessor;
        this.releaseProcessor = releaseProcessor;
        this.observability = observability;
    }

    @KafkaListener(topics = "${flashsale.inventory.regular-hold.commands-topic}",
            groupId = "${flashsale.inventory.regular-hold.command-consumer-group}",
            containerFactory = "inventoryRegularHoldKafkaListenerContainerFactory",
            autoStartup = "${flashsale.inventory.regular-hold.command-consumer-enabled:false}")
    public void onConfirm(ConsumerRecord<String, SpecificRecord> record, Acknowledgment acknowledgment) {
        try {
            if (record.value() instanceof ConfirmRegularStockHoldV1) {
                confirmProcessor.process(confirmMapper.map(asConfirmRecord(record)));
            } else if (record.value() instanceof ReleaseRegularStockHoldV1) {
                releaseProcessor.process(releaseMapper.map(asReleaseRecord(record)));
            } else {
                throw new RegularHoldCommandRecordException("Unsupported regular hold command record type");
            }
            acknowledgment.acknowledge();
        } catch (RegularHoldCommandRecordException | RegularHoldCommandConflictException exception) {
            observability.recordDltPublication("commands");
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private ConsumerRecord<String, ConfirmRegularStockHoldV1> asConfirmRecord(ConsumerRecord<String, SpecificRecord> record) {
        return (ConsumerRecord<String, ConfirmRegularStockHoldV1>) (ConsumerRecord<?, ?>) record;
    }

    @SuppressWarnings("unchecked")
    private ConsumerRecord<String, ReleaseRegularStockHoldV1> asReleaseRecord(ConsumerRecord<String, SpecificRecord> record) {
        return (ConsumerRecord<String, ReleaseRegularStockHoldV1>) (ConsumerRecord<?, ?>) record;
    }
}
