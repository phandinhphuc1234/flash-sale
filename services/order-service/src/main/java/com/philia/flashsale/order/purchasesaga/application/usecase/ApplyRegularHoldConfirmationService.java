package com.philia.flashsale.order.purchasesaga.application.usecase;

import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyRegularHoldConfirmationPort;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldConfirmationResult;
import java.util.Objects;

/** Delegates the regular terminal Saga transition to its transaction-owning persistence adapter. */
public final class ApplyRegularHoldConfirmationService implements ApplyRegularHoldConfirmationUseCase {
    private final ApplyRegularHoldConfirmationPort persistence;

    public ApplyRegularHoldConfirmationService(ApplyRegularHoldConfirmationPort persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public RegularHoldConfirmationResult apply(RegularStockHoldConfirmedCommand command) {
        return persistence.apply(Objects.requireNonNull(command, "command"));
    }
}
