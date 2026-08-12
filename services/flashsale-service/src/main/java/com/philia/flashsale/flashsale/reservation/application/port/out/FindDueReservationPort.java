package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.result.ReservationExpiryCandidate;
import java.time.Instant;
import java.util.List;

public interface FindDueReservationPort {
    List<ReservationExpiryCandidate> findDue(Instant now, int batchSize);
}
