package com.philia.flashsale.flashsale.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationConfirmationPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationConfirmationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult;
import com.philia.flashsale.flashsale.reservation.application.usecase.ConfirmReservationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmReservationServiceTests {

    @Test
    void reconcilesRedisOnlyWhenTheDurableReservationIsConfirmed() {
        var persistence = mock(PersistReservationConfirmationPort.class);
        var redis = mock(ReconcileReservationConfirmationPort.class);
        var command = mock(ConfirmReservationCommand.class);
        var result = result(ReservationConfirmationResult.Status.CONFIRMED);
        when(persistence.confirm(command)).thenReturn(result);

        var actual = new ConfirmReservationService(persistence, redis).confirm(command);

        assertThat(actual).isSameAs(result);
        verify(redis).confirm(command, result);
    }

    @Test
    void doesNotRunConfirmationLuaForAReleasedCurrentStateResult() {
        var persistence = mock(PersistReservationConfirmationPort.class);
        var redis = mock(ReconcileReservationConfirmationPort.class);
        var command = mock(ConfirmReservationCommand.class);
        var result = result(ReservationConfirmationResult.Status.RELEASED);
        when(persistence.confirm(command)).thenReturn(result);

        var actual = new ConfirmReservationService(persistence, redis).confirm(command);

        assertThat(actual).isSameAs(result);
        verifyNoInteractions(redis);
    }

    private ReservationConfirmationResult result(ReservationConfirmationResult.Status status) {
        return new ReservationConfirmationResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), status, Instant.parse("2030-01-01T10:00:00Z"));
    }
}
