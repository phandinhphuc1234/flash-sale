package com.philia.flashsale.flashsale.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.flashsale.reservation.application.command.ReserveCampaignQuotaCommand;
import com.philia.flashsale.flashsale.reservation.application.port.out.ExecuteAtomicReservationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationFingerprintService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationIdentityFactory;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReserveCampaignQuotaService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReserveCampaignQuotaServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void createsHashesAndStableIdentitiesBeforeDelegatingAtomicDecision() {
        AtomicReference<ExecuteAtomicReservationPort.AtomicReservationRequest> captured = new AtomicReference<>();
        ExecuteAtomicReservationPort port = request -> {
            captured.set(request);
            return ReservationDecisionResult.rejected(ReservationDecisionResult.Outcome.SOLD_OUT);
        };
        ReserveCampaignQuotaService service = service(port);
        ReserveCampaignQuotaCommand command = command("Case-Sensitive-Key");

        ReservationDecisionResult result = service.reserve(command);

        assertThat(result.outcome()).isEqualTo(ReservationDecisionResult.Outcome.SOLD_OUT);
        var request = captured.get();
        assertThat(request.purchaseRequestId()).isNotNull();
        assertThat(request.reservationId()).isNotNull();
        assertThat(request.eventId()).isNotNull();
        assertThat(request.idempotencyKeyHash()).hasSize(64);
        assertThat(request.requestHash()).hasSize(64);
        assertThat(request.acceptedAt()).isEqualTo(NOW);
        assertThat(request.expiresAt()).isEqualTo(NOW.plusSeconds(5 * 60));
        assertThat(request.retainedUntil()).isEqualTo(command.campaignEndsAt().plusSeconds(24 * 60 * 60));
    }

    @Test
    void sameRawKeyAndDifferentCasingProduceDifferentKeyHashes() {
        ReservationFingerprintService fingerprints = new ReservationFingerprintService();

        assertThat(fingerprints.hashIdempotencyKey("abc"))
                .isNotEqualTo(fingerprints.hashIdempotencyKey("ABC"));
    }

    @Test
    void rejectsARequestEndingBeforeTheAdmissionClock() {
        ReserveCampaignQuotaService service = service(request -> ReservationDecisionResult.rejected(
                ReservationDecisionResult.Outcome.SOLD_OUT));
        ReserveCampaignQuotaCommand command = new ReserveCampaignQuotaCommand(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, "key", NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.reserve(command)).isInstanceOf(IllegalArgumentException.class);
    }

    private ReserveCampaignQuotaService service(ExecuteAtomicReservationPort port) {
        return new ReserveCampaignQuotaService(port, new ReservationFingerprintService(),
                new ReservationIdentityFactory(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5),
                Duration.ofHours(24));
    }

    private ReserveCampaignQuotaCommand command(String key) {
        return new ReserveCampaignQuotaCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, key,
                NOW.plusSeconds(60 * 60), "00-trace", "");
    }
}
