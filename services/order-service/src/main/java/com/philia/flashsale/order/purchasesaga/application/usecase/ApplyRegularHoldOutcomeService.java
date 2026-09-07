package com.philia.flashsale.order.purchasesaga.application.usecase;

import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldOutcomeCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldOutcomeUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyRegularHoldOutcomePort;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldOutcomeResult;
import java.util.Objects;

/** Delegates released/expired regular-hold convergence to the transaction-owning adapter. */
public final class ApplyRegularHoldOutcomeService implements ApplyRegularHoldOutcomeUseCase {
    private final ApplyRegularHoldOutcomePort persistence;

    public ApplyRegularHoldOutcomeService(ApplyRegularHoldOutcomePort persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public RegularHoldOutcomeResult apply(RegularStockHoldOutcomeCommand command) {
        return persistence.apply(Objects.requireNonNull(command, "command"));
    }
}
