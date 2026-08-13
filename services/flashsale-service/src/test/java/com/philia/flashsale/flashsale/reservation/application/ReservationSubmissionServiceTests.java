package com.philia.flashsale.flashsale.reservation.application;

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
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReservationSubmissionServiceTests {
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final UUID VARIANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant END = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void persistsWinnerBeforeReturningAccepted() {
        AcceptedReservationSnapshot snapshot = snapshot();
        AtomicReference<AcceptedReservationSnapshot> persisted = new AtomicReference<>();
        ReserveCampaignQuotaUseCase admission = command ->
                ReservationDecisionResult.accepted(ReservationDecisionResult.Outcome.ACCEPTED_NEW, snapshot);
        PersistAcceptedPurchasePort persistence = persisted::set;

        ReservationSubmissionResult result = service(admission, persistence).submit(command());

        assertThat(result.outcome()).isEqualTo(ReservationSubmissionResult.Outcome.ACCEPTED_NEW);
        assertThat(result.snapshot()).isSameAs(snapshot);
        assertThat(persisted.get()).isSameAs(snapshot);
    }

    @Test
    void reportsPendingWhenPostgresAcceptanceFails() {
        ReserveCampaignQuotaUseCase admission = command ->
                ReservationDecisionResult.accepted(ReservationDecisionResult.Outcome.ACCEPTED_NEW, snapshot());

        ReservationSubmissionResult result = service(admission, value -> {
            throw new IllegalStateException("database unavailable");
        }).submit(command());

        assertThat(result.outcome()).isEqualTo(ReservationSubmissionResult.Outcome.ACCEPTANCE_PENDING);
        assertThat(result.snapshot()).isNull();
    }

    @Test
    void failsClosedWhenProjectionEndIsUnavailable() {
        ReservationSubmissionResult result = new ReservationSubmissionService(
                command -> { throw new AssertionError("admission must not run"); },
                campaignId -> Optional.empty(),
                value -> { throw new AssertionError("persistence must not run"); })
                .submit(command());

        assertThat(result.outcome()).isEqualTo(ReservationSubmissionResult.Outcome.PROJECTION_UNAVAILABLE);
    }

    @Test
    void preservesAtomicAdmissionRejection() {
        ReserveCampaignQuotaUseCase admission = command ->
                ReservationDecisionResult.rejected(ReservationDecisionResult.Outcome.SOLD_OUT);

        ReservationSubmissionResult result = service(admission, value -> {
            throw new AssertionError("rejected decisions must not persist");
        }).submit(command());

        assertThat(result.outcome()).isEqualTo(ReservationSubmissionResult.Outcome.SOLD_OUT);
    }

    @Test
    void rejectsWinnerThatExpiresBeforeDurableCommit() {
        AcceptedReservationSnapshot snapshot = snapshot();
        ReservationSubmissionService service = new ReservationSubmissionService(
                command -> ReservationDecisionResult.accepted(
                        ReservationDecisionResult.Outcome.ACCEPTED_NEW, snapshot),
                campaignId -> Optional.of(END),
                value -> { throw new AssertionError("expired winner must not persist"); },
                Clock.fixed(snapshot.expiresAt(), ZoneOffset.UTC));

        ReservationSubmissionResult result = service.submit(command());

        assertThat(result.outcome()).isEqualTo(ReservationSubmissionResult.Outcome.RESERVATION_EXPIRED);
    }

    private ReservationSubmissionService service(ReserveCampaignQuotaUseCase admission,
            PersistAcceptedPurchasePort persistence) {
        LoadCampaignEndPort ends = campaignId -> Optional.of(END);
        return new ReservationSubmissionService(admission, ends, persistence);
    }

    private SubmitReservationCommand command() {
        return new SubmitReservationCommand(CAMPAIGN_ID, VARIANT_ID, USER_ID, 1, "submit-key", null, null);
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
