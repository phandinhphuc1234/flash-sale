package com.philia.flashsale.order.purchasesaga.application.usecase;

import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyPurchaseReservationConfirmationPort;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationConfirmationResult;
import java.util.Objects;

/** Delegates terminal Saga finalization to the transaction-owning persistence adapter. */
public final class ApplyPurchaseReservationConfirmationService
        implements ApplyPurchaseReservationConfirmationUseCase {
    private final ApplyPurchaseReservationConfirmationPort persistence;

    public ApplyPurchaseReservationConfirmationService(ApplyPurchaseReservationConfirmationPort persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public PurchaseReservationConfirmationResult apply(PurchaseReservationConfirmedCommand command) {
        return persistence.apply(Objects.requireNonNull(command, "command"));
    }
}
