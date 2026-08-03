package com.philia.flashsale.campaign.scheduleoperation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.campaign.scheduleoperation.application.command.PrepareScheduleOperationCommand;
import com.philia.flashsale.campaign.scheduleoperation.application.exception.ScheduleOperationInProgressException;
import com.philia.flashsale.campaign.scheduleoperation.application.exception.ScheduleOperationRequestConflictException;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.SaveScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.result.ScheduleOperationPreparationResult;
import com.philia.flashsale.campaign.scheduleoperation.application.usecase.PrepareScheduleOperationService;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationFingerprint;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Focused application tests for T057's short transaction and identity rules. */
class PrepareScheduleOperationServiceTests {

    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-03T01:00:00Z");

    @Test
    void createsOperationWithStableInventoryRequestIdentity() {
        InMemoryOperations operations = new InMemoryOperations();
        PrepareScheduleOperationService service = service(operations);
        ScheduleOperationFingerprint fingerprint = fingerprint("draft-v1");

        ScheduleOperationPreparationResult result = service.prepare(command("key-1", fingerprint, 0L));

        assertThat(result.replayed()).isFalse();
        assertThat(result.operation().status().name()).isEqualTo("STARTED");
        assertThat(result.operation().inventoryRequestId()).isNotNull();
        assertThat(operations.saved).isSameAs(result.operation());
    }

    @Test
    void sameKeyAndIdentityReplaysExistingOperationWithoutReplacingItsRequestId() {
        InMemoryOperations operations = new InMemoryOperations();
        PrepareScheduleOperationService service = service(operations);
        ScheduleOperationFingerprint fingerprint = fingerprint("draft-v1");

        ScheduleOperationPreparationResult first = service.prepare(command("key-1", fingerprint, 0L));
        ScheduleOperationPreparationResult replay = service.prepare(command("key-1", fingerprint, 0L));

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.operation().id()).isEqualTo(first.operation().id());
        assertThat(replay.operation().inventoryRequestId()).isEqualTo(first.operation().inventoryRequestId());
        assertThat(operations.saveCount).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentIdentityIsRejectedBeforeASecondSave() {
        InMemoryOperations operations = new InMemoryOperations();
        PrepareScheduleOperationService service = service(operations);

        service.prepare(command("key-1", fingerprint("draft-v1"), 0L));

        assertThatThrownBy(() -> service.prepare(command("key-1", fingerprint("draft-v2"), 0L)))
                .isInstanceOf(ScheduleOperationRequestConflictException.class);
        assertThat(operations.saveCount).isEqualTo(1);
    }

    @Test
    void differentKeyCannotStartWhileAnotherOperationIsInFlight() {
        InMemoryOperations operations = new InMemoryOperations();
        PrepareScheduleOperationService service = service(operations);
        service.prepare(command("key-1", fingerprint("draft-v1"), 0L));
        operations.inFlight = true;

        assertThatThrownBy(() -> service.prepare(command("key-2", fingerprint("draft-v1"), 0L)))
                .isInstanceOf(ScheduleOperationInProgressException.class);
    }

    private PrepareScheduleOperationService service(InMemoryOperations operations) {
        return new PrepareScheduleOperationService(operations, operations, () -> NOW);
    }

    private PrepareScheduleOperationCommand command(
            String key, ScheduleOperationFingerprint fingerprint, long version) {
        return new PrepareScheduleOperationCommand(
                CAMPAIGN_ID,
                key,
                fingerprint,
                version,
                "admin-1",
                "campaign-service",
                "trace-1");
    }

    private ScheduleOperationFingerprint fingerprint(String value) {
        return ScheduleOperationFingerprint.fromCanonicalPayload(value);
    }

    private static final class InMemoryOperations
            implements LoadScheduleOperationPort, SaveScheduleOperationPort {
        private ScheduleOperation saved;
        private int saveCount;
        private boolean inFlight;

        @Override
        public Optional<ScheduleOperation> findByCampaignIdAndIdempotencyKey(UUID campaignId, String idempotencyKey) {
            return saved != null && saved.campaignId().equals(campaignId)
                    && saved.idempotencyKey().equals(idempotencyKey)
                    ? Optional.of(saved)
                    : Optional.empty();
        }

        @Override
        public Optional<ScheduleOperation> findByInventoryRequestId(UUID inventoryRequestId) {
            return saved != null && saved.inventoryRequestId().equals(inventoryRequestId)
                    ? Optional.of(saved)
                    : Optional.empty();
        }

        @Override
        public boolean existsInFlightByCampaignId(UUID campaignId) {
            return inFlight || (saved != null && saved.campaignId().equals(campaignId)
                    && saved.status().isInFlight());
        }

        @Override
        public ScheduleOperation save(ScheduleOperation operation) {
            saved = operation;
            saveCount++;
            return operation;
        }
    }
}
