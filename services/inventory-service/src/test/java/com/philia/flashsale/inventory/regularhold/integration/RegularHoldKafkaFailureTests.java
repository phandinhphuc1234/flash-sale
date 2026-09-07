package com.philia.flashsale.inventory.regularhold.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1;
import com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka.ConfirmRegularHoldAvroMapper;
import com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka.RegularHoldCommandKafkaConsumer;
import com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka.RegularHoldCommandRecordException;
import com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka.ReleaseRegularHoldAvroMapper;
import com.philia.flashsale.inventory.regularhold.application.port.in.ProcessConfirmRegularHoldCommandUseCase;
import com.philia.flashsale.inventory.regularhold.application.port.in.ProcessReleaseRegularHoldCommandUseCase;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

/** Contract-boundary failure coverage for malformed and unsupported Inventory commands. */
class RegularHoldKafkaFailureTests {

    @Test
    void malformedConfirmEnvelopeIsRejectedBeforeTheApplicationPort() {
        UUID orderId = UUID.randomUUID();
        var value = new ConfirmRegularStockHoldV1(UUID.randomUUID(), "ConfirmRegularStockHold", 1,
                "order-service", "PURCHASE_SAGA", UUID.randomUUID(), 1L, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-09-04T13:31:00Z"), null, null, null);

        assertThatThrownBy(() -> new ConfirmRegularHoldAvroMapper().map(new ConsumerRecord<>(
                "flashsale.inventory.regular-hold.commands.v1", 0, 0L, orderId.toString(), value)))
                .isInstanceOf(RegularHoldCommandRecordException.class);
    }

    @Test
    void unsupportedSpecificRecordIsNonRetryableAndIsNotAcknowledged() {
        var consumer = new RegularHoldCommandKafkaConsumer(new ConfirmRegularHoldAvroMapper(),
                new ReleaseRegularHoldAvroMapper(), mock(ProcessConfirmRegularHoldCommandUseCase.class),
                mock(ProcessReleaseRegularHoldCommandUseCase.class));
        var acknowledgment = mock(Acknowledgment.class);
        SpecificRecord unsupported = mock(SpecificRecord.class);

        assertThatThrownBy(() -> consumer.onConfirm(new ConsumerRecord<>(
                "flashsale.inventory.regular-hold.commands.v1", 2, 9L, "order", unsupported), acknowledgment))
                .isInstanceOf(RegularHoldCommandRecordException.class)
                .hasMessageContaining("Unsupported");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void aMalformedRecordNeverReachesEitherCommandProcessor() {
        var confirm = mock(ProcessConfirmRegularHoldCommandUseCase.class);
        var release = mock(ProcessReleaseRegularHoldCommandUseCase.class);
        var consumer = new RegularHoldCommandKafkaConsumer(new ConfirmRegularHoldAvroMapper(),
                new ReleaseRegularHoldAvroMapper(), confirm, release);
        var acknowledgment = mock(Acknowledgment.class);
        SpecificRecord unsupported = mock(SpecificRecord.class);

        assertThatThrownBy(() -> consumer.onConfirm(new ConsumerRecord<>(
                "flashsale.inventory.regular-hold.commands.v1", 0, 1L, "order", unsupported), acknowledgment))
                .isInstanceOf(RegularHoldCommandRecordException.class);
        verify(confirm, never()).process(any());
        verify(release, never()).process(any());
    }
}
