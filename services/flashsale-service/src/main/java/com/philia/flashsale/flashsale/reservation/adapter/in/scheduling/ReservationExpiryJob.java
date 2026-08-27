package com.philia.flashsale.flashsale.reservation.adapter.in.scheduling;

import com.philia.flashsale.flashsale.reservation.application.port.in.ExpireReservationsUseCase;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseRequestJpaRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Retries durable-first expiry. A dependency failure leaves both durable rows and Redis state retryable. */
@Component
@ConditionalOnBean({ExpireReservationsUseCase.class, FlashSaleReservationJpaRepository.class,
        PurchaseRequestJpaRepository.class})
public final class ReservationExpiryJob {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationExpiryJob.class);
    private final ExpireReservationsUseCase expiry;
    private final Clock clock;

    @Autowired
    public ReservationExpiryJob(ExpireReservationsUseCase expiry) {
        this(expiry, Clock.systemUTC());
    }

    ReservationExpiryJob(ExpireReservationsUseCase expiry, Clock clock) {
        this.expiry = expiry;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000L)
    public void expireReservations() {
        try {
            expiry.expireDueReservations(clock.instant());
        } catch (RuntimeException exception) {
            LOG.warn("flashsale_reservation_expiry_failed exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
