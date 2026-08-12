package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis;

import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationHandoffRedisAdapter;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationAcceptanceResult;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationAcceptanceFlow;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.scheduling.annotation.Scheduled;

/** Drives Redis handoff entries into the same durable acceptance use case as the request thread. */
public final class ReservationHandoffStreamConsumer {
    private final ReservationHandoffConsumerGroupInitializer initializer;
    private final ReservationHandoffRedisAdapter handoff;
    private final ReservationHandoffMessageMapper mapper;
    private final ReservationAcceptanceFlow acceptance;
    private final PersistAcceptedPurchasePort durableAcceptance;
    private final Clock clock;

    public ReservationHandoffStreamConsumer(ReservationHandoffConsumerGroupInitializer initializer,
            ReservationHandoffRedisAdapter handoff, ReservationAcceptanceFlow acceptance,
            PersistAcceptedPurchasePort durableAcceptance) {
        this(initializer, handoff, new ReservationHandoffMessageMapper(), acceptance, durableAcceptance,
                Clock.systemUTC());
    }

    public ReservationHandoffStreamConsumer(ReservationHandoffConsumerGroupInitializer initializer,
            ReservationHandoffRedisAdapter handoff, ReservationHandoffMessageMapper mapper,
            ReservationAcceptanceFlow acceptance, PersistAcceptedPurchasePort durableAcceptance, Clock clock) {
        this.initializer = Objects.requireNonNull(initializer, "initializer");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.acceptance = Objects.requireNonNull(acceptance, "acceptance");
        this.durableAcceptance = Objects.requireNonNull(durableAcceptance, "durableAcceptance");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${flashsale.redis.poll-timeout:1s}")
    public void poll() {
        pollOnce();
    }

    public void pollOnce() {
        initializer.initialize();
        process(handoff.reclaimPendingEntries());
        process(handoff.readNewEntries());
    }

    private void process(List<MapRecord<String, String, String>> records) {
        for (MapRecord<String, String, String> record : records) {
            process(record);
        }
    }

    private void process(MapRecord<String, String, String> record) {
        try {
            var snapshot = mapper.map(record);
            var result = acceptance.accept(snapshot, record.getId().getValue(), clock.instant());
            if (result.outcome() == ReservationAcceptanceResult.Outcome.RESERVATION_EXPIRED) {
                // The durable adapter writes/observes the terminal EXPIRED tombstone before ACK.
                durableAcceptance.persist(snapshot);
                handoff.acknowledge(snapshot.reservationId(), record.getId().getValue());
            }
        } catch (RuntimeException ignored) {
            // Keep the entry pending. A later poll or XAUTOCLAIM retries the immutable command.
        }
    }
}
