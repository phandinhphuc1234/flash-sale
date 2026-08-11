package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Converts the small Redis Lua tuple into an infrastructure-free application result. */
public final class ReservationLuaResultMapper {
    public ReservationDecisionResult map(List<?> result) {
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("Reservation script returned no result");
        }
        ReservationDecisionResult.Outcome outcome = outcome(result.get(0));
        if (!outcome.isAccepted()) {
            return ReservationDecisionResult.rejected(outcome);
        }
        if (result.size() < 19) {
            throw new IllegalStateException("Accepted reservation script result is incomplete");
        }
        AcceptedReservationSnapshot snapshot = new AcceptedReservationSnapshot(
                uuid(result, 1), uuid(result, 2), uuid(result, 3), uuid(result, 4), uuid(result, 5), uuid(result, 6),
                uuid(result, 7), text(result, 8), new BigDecimal(text(result, 9)), text(result, 10),
                Long.parseLong(text(result, 11)), text(result, 12), text(result, 13), instant(result, 14),
                instant(result, 15), instant(result, 16), optional(result, 17), optional(result, 18));
        return ReservationDecisionResult.accepted(outcome, snapshot);
    }

    private ReservationDecisionResult.Outcome outcome(Object value) {
        try {
            return ReservationDecisionResult.Outcome.valueOf(text(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown reservation script result", exception);
        }
    }

    private UUID uuid(List<?> values, int index) {
        return UUID.fromString(text(values, index));
    }

    private Instant instant(List<?> values, int index) {
        return Instant.ofEpochMilli(Long.parseLong(text(values, index)));
    }

    private String optional(List<?> values, int index) {
        if (values.size() <= index || values.get(index) == null || "false".equals(values.get(index).toString())) {
            return null;
        }
        return text(values, index);
    }

    private String text(List<?> values, int index) {
        return text(values.get(index));
    }

    private String text(Object value) {
        if (value == null) {
            throw new IllegalStateException("Reservation script returned null");
        }
        return value.toString();
    }
}
