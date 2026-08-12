package com.philia.flashsale.flashsale.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.reservation.application.port.out.AcknowledgeReservationHandoffPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationAcceptanceResult;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationAcceptanceFlow;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReserveCampaignQuotaFlowTests {
    private static final Instant ACCEPTED_AT = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant EXPIRES_AT = ACCEPTED_AT.plusSeconds(300);

    @Test
    void durableSuccessPersistsBeforeAcknowledgingHandoff() {
        List<String> calls = new ArrayList<>();
        ReservationAcceptanceFlow flow = new ReservationAcceptanceFlow(
                snapshot -> calls.add("persist"),
                (reservationId, entryId) -> calls.add("ack:" + entryId));

        ReservationAcceptanceResult result = flow.accept(snapshot(), "0-1", ACCEPTED_AT.plusSeconds(1));

        assertThat(result.outcome()).isEqualTo(ReservationAcceptanceResult.Outcome.DURABLY_ACCEPTED);
        assertThat(result.handoffAcknowledged()).isTrue();
        assertThat(calls).containsExactly("persist", "ack:0-1");
    }

    @Test
    void durableFailureLeavesAcceptancePendingAndDoesNotAcknowledge() {
        List<String> calls = new ArrayList<>();
        PersistAcceptedPurchasePort persistence = snapshot -> {
            calls.add("persist");
            throw new IllegalStateException("database unavailable");
        };
        ReservationAcceptanceFlow flow = new ReservationAcceptanceFlow(persistence,
                (reservationId, entryId) -> calls.add("ack"));

        ReservationAcceptanceResult result = flow.accept(snapshot(), "0-1", ACCEPTED_AT.plusSeconds(1));

        assertThat(result.outcome()).isEqualTo(ReservationAcceptanceResult.Outcome.ACCEPTANCE_PENDING);
        assertThat(result.handoffAcknowledged()).isFalse();
        assertThat(calls).containsExactly("persist");
    }

    @Test
    void expiryBecomesTerminalBeforePersistenceAtTheEligibilityBoundary() {
        List<String> calls = new ArrayList<>();
        ReservationAcceptanceFlow flow = new ReservationAcceptanceFlow(
                snapshot -> calls.add("persist"),
                (reservationId, entryId) -> calls.add("ack"));

        ReservationAcceptanceResult result = flow.accept(snapshot(), "0-1", EXPIRES_AT);

        assertThat(result.outcome()).isEqualTo(ReservationAcceptanceResult.Outcome.RESERVATION_EXPIRED);
        assertThat(calls).isEmpty();
    }

    @Test
    void acknowledgementFailureStillReportsDurableAcceptanceForReplayRecovery() {
        ReservationAcceptanceFlow flow = new ReservationAcceptanceFlow(snapshot -> {
        }, (reservationId, entryId) -> {
            throw new IllegalStateException("stream unavailable");
        });

        ReservationAcceptanceResult result = flow.accept(snapshot(), "0-1", ACCEPTED_AT.plusSeconds(1));

        assertThat(result.outcome()).isEqualTo(ReservationAcceptanceResult.Outcome.DURABLY_ACCEPTED);
        assertThat(result.handoffAcknowledged()).isFalse();
    }

    private AcceptedReservationSnapshot snapshot() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        return new AcceptedReservationSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 1, hash, hash, ACCEPTED_AT, EXPIRES_AT,
                EXPIRES_AT.plusSeconds(24 * 60 * 60), "00-trace", "");
    }
}
