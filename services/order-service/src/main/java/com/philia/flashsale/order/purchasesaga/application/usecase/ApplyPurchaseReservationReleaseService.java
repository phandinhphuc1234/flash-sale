package com.philia.flashsale.order.purchasesaga.application.usecase;

import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationReleaseUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyPurchaseReservationReleasePort;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationReleaseResult;
import java.util.Objects;

public final class ApplyPurchaseReservationReleaseService implements ApplyPurchaseReservationReleaseUseCase {
    private final ApplyPurchaseReservationReleasePort persistence;
    public ApplyPurchaseReservationReleaseService(ApplyPurchaseReservationReleasePort persistence) { this.persistence = Objects.requireNonNull(persistence, "persistence"); }
    @Override public PurchaseReservationReleaseResult apply(PurchaseReservationReleasedCommand command) { return persistence.apply(Objects.requireNonNull(command, "command")); }
}
