package com.philia.flashsale.campaign.scheduleoperation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.SaveScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperation;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationFingerprint;
import com.philia.flashsale.campaign.scheduleoperation.domain.model.ScheduleOperationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain-level contract tests for the durable schedule-operation identity workflow. */
class ScheduleOperationDomainTests {

    @Test
    void fingerprintIsDeterministicSha256OfCanonicalIdentity() {
        ScheduleOperationFingerprint first = ScheduleOperationFingerprint.fromCanonicalPayload(
                "campaign=campaign-1|version=3|variant=variant-1|price=1000");
        ScheduleOperationFingerprint second = ScheduleOperationFingerprint.fromCanonicalPayload(
                "campaign=campaign-1|version=3|variant=variant-1|price=1000");

        assertThat(first).isEqualTo(second);
        assertThat(first.value()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void operationUsesStableIdentityAcrossFailureAndRetry() {
        UUID operationId = UUID.randomUUID();
        UUID inventoryRequestId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-03T00:00:00Z");
        ScheduleOperationFingerprint fingerprint = ScheduleOperationFingerprint.fromCanonicalPayload("identity");

        ScheduleOperation operation = ScheduleOperation.start(
                operationId,
                UUID.randomUUID(),
                "schedule-key",
                inventoryRequestId,
                fingerprint,
                4L,
                "admin-1",
                "campaign-service",
                "trace-1",
                now);

        operation.markFailed("INVENTORY_REJECTED", "Inventory is insufficient");
        operation.retry(now.plusSeconds(1));

        assertThat(operation.status()).isEqualTo(ScheduleOperationStatus.STARTED);
        assertThat(operation.attemptCount()).isEqualTo(2);
        assertThat(operation.id()).isEqualTo(operationId);
        assertThat(operation.inventoryRequestId()).isEqualTo(inventoryRequestId);
        assertThat(operation.idempotencyKey()).isEqualTo("schedule-key");
        assertThat(operation.requestHash()).isEqualTo(fingerprint.value());
        assertThat(operation.campaignVersion()).isEqualTo(4L);
    }

    @Test
    void onlyApprovedStatusTransitionsAreAllowed() {
        ScheduleOperation operation = ScheduleOperation.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "schedule-key",
                UUID.randomUUID(),
                ScheduleOperationFingerprint.fromCanonicalPayload("identity"),
                0L,
                "admin-1",
                "campaign-service",
                "trace-1",
                Instant.now());

        operation.markInventoryAllocated();
        operation.markCompleted();

        assertThat(operation.status()).isEqualTo(ScheduleOperationStatus.COMPLETED);
        assertThatIllegalStateException().isThrownBy(operation::markInventoryAllocated);
    }

    @Test
    void portsExposeCapabilityOrientedScheduleOperationPersistence() {
        assertThat(LoadScheduleOperationPort.class).isInterface();
        assertThat(SaveScheduleOperationPort.class).isInterface();
    }
}
