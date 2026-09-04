package com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1;
import com.philia.flashsale.inventory.regularhold.application.exception.RegularHoldCommandConflictException;
import com.philia.flashsale.inventory.regularhold.application.port.in.ProcessConfirmRegularHoldCommandUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Acknowledges only after the local inbox, stock result, and durable outbox transaction commits. */
@Component
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.command-consumer-enabled", havingValue = "true")
public class RegularHoldCommandKafkaConsumer {
    private final ConfirmRegularHoldAvroMapper mapper;
    private final ProcessConfirmRegularHoldCommandUseCase processor;

    public RegularHoldCommandKafkaConsumer(ConfirmRegularHoldAvroMapper mapper,
            ProcessConfirmRegularHoldCommandUseCase processor) {
        this.mapper = mapper;
        this.processor = processor;
    }

    @KafkaListener(topics = "${flashsale.inventory.regular-hold.commands-topic}",
            groupId = "${flashsale.inventory.regular-hold.command-consumer-group}",
            containerFactory = "inventoryRegularHoldKafkaListenerContainerFactory",
            autoStartup = "${flashsale.inventory.regular-hold.command-consumer-enabled:false}")
    public void onConfirm(ConsumerRecord<String, ConfirmRegularStockHoldV1> record, Acknowledgment acknowledgment) {
        try {
            processor.process(mapper.map(record));
            acknowledgment.acknowledge();
        } catch (RegularHoldCommandRecordException | RegularHoldCommandConflictException exception) {
            throw exception;
        }
    }
}
