package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.command.ReserveCampaignQuotaCommand;
import com.philia.flashsale.flashsale.reservation.application.command.SubmitReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.in.ReserveCampaignQuotaUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.in.SubmitReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadCampaignEndPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationSubmissionResult;
import java.time.Instant;
import java.time.Clock;
import java.util.Objects;

/** Composes atomic admission and PostgreSQL acceptance without exposing adapter types to web code. */
public final class ReservationSubmissionService implements SubmitReservationUseCase {
    private final ReserveCampaignQuotaUseCase admission;
    private final LoadCampaignEndPort campaignEnds;
    private final PersistAcceptedPurchasePort durableAcceptance;
    private final Clock clock;

    public ReservationSubmissionService(ReserveCampaignQuotaUseCase admission,
            LoadCampaignEndPort campaignEnds, PersistAcceptedPurchasePort durableAcceptance) {
        this(admission, campaignEnds, durableAcceptance, Clock.systemUTC());
    }

    public ReservationSubmissionService(ReserveCampaignQuotaUseCase admission,
            LoadCampaignEndPort campaignEnds, PersistAcceptedPurchasePort durableAcceptance, Clock clock) {
        this.admission = Objects.requireNonNull(admission, "admission");
        this.campaignEnds = Objects.requireNonNull(campaignEnds, "campaignEnds");
        this.durableAcceptance = Objects.requireNonNull(durableAcceptance, "durableAcceptance");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ReservationSubmissionResult submit(SubmitReservationCommand command) {
        Objects.requireNonNull(command, "command");
        final Instant campaignEndsAt;
        try {
            campaignEndsAt = campaignEnds.loadEndsAt(command.campaignId()).orElse(null);
        } catch (RuntimeException exception) {
            return rejected(ReservationSubmissionResult.Outcome.PROJECTION_UNAVAILABLE);
        }
        if (campaignEndsAt == null) {
            return rejected(ReservationSubmissionResult.Outcome.PROJECTION_UNAVAILABLE);
        }

        ReservationDecisionResult decision;
        try {
            decision = admission.reserve(new ReserveCampaignQuotaCommand(
                    command.campaignId(), command.variantId(), command.userId(), command.quantity(),
                    command.idempotencyKey(), campaignEndsAt, command.traceparent(), command.tracestate()));
        } catch (IllegalArgumentException exception) {
            if ("campaignEndsAt must be after the admission instant".equals(exception.getMessage())) {
                return rejected(ReservationSubmissionResult.Outcome.CAMPAIGN_ENDED);
            }
            throw exception;
        } catch (RuntimeException exception) {
            return rejected(ReservationSubmissionResult.Outcome.REDIS_UNAVAILABLE);
        }

        if (!decision.isAccepted()) {
            return rejected(map(decision.outcome()));
        }
        if (!clock.instant().isBefore(decision.snapshot().expiresAt())) {
            return rejected(ReservationSubmissionResult.Outcome.RESERVATION_EXPIRED);
        }
        try {
            durableAcceptance.persist(decision.snapshot());
        } catch (RuntimeException exception) {
            // The Redis Stream entry remains recoverable; the caller must retry the same key.
            return rejected(ReservationSubmissionResult.Outcome.ACCEPTANCE_PENDING);
        }
        return new ReservationSubmissionResult(map(decision.outcome()), decision.snapshot());
    }

    private ReservationSubmissionResult rejected(ReservationSubmissionResult.Outcome outcome) {
        return new ReservationSubmissionResult(outcome, null);
    }

    private ReservationSubmissionResult.Outcome map(ReservationDecisionResult.Outcome outcome) {
        return ReservationSubmissionResult.Outcome.valueOf(outcome.name());
    }
}
