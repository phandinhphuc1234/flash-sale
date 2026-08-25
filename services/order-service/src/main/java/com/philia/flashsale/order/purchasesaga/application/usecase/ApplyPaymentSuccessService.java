package com.philia.flashsale.order.purchasesaga.application.usecase;

import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyPaymentSuccessPort;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Creates a deterministic confirm command identity and delegates the atomic transition. */
public final class ApplyPaymentSuccessService implements ApplyPaymentSuccessUseCase {
    private final ApplyPaymentSuccessPort persistence;

    public ApplyPaymentSuccessService(ApplyPaymentSuccessPort persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public PaymentSuccessResult apply(PaymentSucceededCommand command) {
        Objects.requireNonNull(command, "command");
        return persistence.apply(command);
    }

    public static UUID confirmCommandId(UUID paymentEventId) {
        Objects.requireNonNull(paymentEventId, "paymentEventId");
        return UUID.nameUUIDFromBytes(("confirm-reservation:" + paymentEventId)
                .getBytes(StandardCharsets.UTF_8));
    }
}
