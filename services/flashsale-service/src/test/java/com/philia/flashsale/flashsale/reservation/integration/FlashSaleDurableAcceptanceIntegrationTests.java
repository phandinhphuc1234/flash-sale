package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.reservation.application.command.SubmitReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.in.ReserveCampaignQuotaUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadCampaignEndPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationSubmissionResult;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationSubmissionService;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Cross-boundary acceptance checkpoint using the same ports as Redis/JPA adapters. */
class FlashSaleDurableAcceptanceIntegrationTests {
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final UUID VARIANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant END = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void createsOneDurableResultBeforeReturningAccepted() {
        AcceptedReservationSnapshot snapshot = snapshot();
        AtomicReference<AcceptedReservationSnapshot> durableRows = new AtomicReference<>();
        AtomicInteger admissionCalls = new AtomicInteger();
        ReserveCampaignQuotaUseCase admission = command -> {
            admissionCalls.incrementAndGet();
            return ReservationDecisionResult.accepted(ReservationDecisionResult.Outcome.ACCEPTED_NEW, snapshot);
        };
        ReservationSubmissionService service = service(admission, durableRows::set);

        ReservationSubmissionResult result = service.submit(command());

        assertThat(result.outcome()).isEqualTo(ReservationSubmissionResult.Outcome.ACCEPTED_NEW);
        assertThat(durableRows.get()).isSameAs(snapshot);
        assertThat(admissionCalls).hasValue(1);
    }

    @Test
    void retriesSameWinnerAfterPostgresFailureWithoutChangingIdentity() {
        AcceptedReservationSnapshot snapshot = snapshot();
        AtomicInteger attempts = new AtomicInteger();
        PersistAcceptedPurchasePort durable = value -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("postgres unavailable");
            }
        };
        ReserveCampaignQuotaUseCase admission = command ->
                ReservationDecisionResult.accepted(
                        attempts.get() == 0 ? ReservationDecisionResult.Outcome.ACCEPTED_NEW
                                : ReservationDecisionResult.Outcome.ACCEPTED_REPLAY,
                        snapshot);
        ReservationSubmissionService service = service(admission, durable);

        ReservationSubmissionResult pending = service.submit(command());
        ReservationSubmissionResult recovered = service.submit(command());

        assertThat(pending.outcome()).isEqualTo(ReservationSubmissionResult.Outcome.ACCEPTANCE_PENDING);
        assertThat(recovered.outcome()).isEqualTo(ReservationSubmissionResult.Outcome.ACCEPTED_REPLAY);
        assertThat(recovered.snapshot().purchaseRequestId()).isEqualTo(snapshot.purchaseRequestId());
        assertThat(recovered.snapshot().reservationId()).isEqualTo(snapshot.reservationId());
    }

    private ReservationSubmissionService service(ReserveCampaignQuotaUseCase admission,
            PersistAcceptedPurchasePort durable) {
        LoadCampaignEndPort ends = campaignId -> Optional.of(END);
        return new ReservationSubmissionService(admission, ends, durable);
    }

    private SubmitReservationCommand command() {
        return new SubmitReservationCommand(CAMPAIGN_ID, VARIANT_ID, USER_ID, 1, "integration-key", null, null);
    }

    private AcceptedReservationSnapshot snapshot() {
        Instant accepted = Instant.parse("2029-12-31T23:59:00Z");
        return new AcceptedReservationSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CAMPAIGN_ID, VARIANT_ID, USER_ID,
                UUID.randomUUID(), "SKU-001", BigDecimal.ONE.setScale(4), "VND", 1,
                "a".repeat(64), "b".repeat(64), accepted, accepted.plusSeconds(300), END.plusSeconds(86400),
                null, null);
    }
}
