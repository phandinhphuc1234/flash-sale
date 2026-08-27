package com.philia.flashsale.order.purchasesaga.application.usecase;

import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentFailureUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.out.ApplyPaymentFailurePort;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentFailureResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Creates a deterministic release command identity and delegates the atomic transition. */
public final class ApplyPaymentFailureService implements ApplyPaymentFailureUseCase {
    private final ApplyPaymentFailurePort persistence;
    public ApplyPaymentFailureService(ApplyPaymentFailurePort persistence) { this.persistence = Objects.requireNonNull(persistence, "persistence"); }
    @Override public PaymentFailureResult apply(PaymentFailedCommand command) { return persistence.apply(Objects.requireNonNull(command, "command")); }
    public static UUID releaseCommandId(UUID paymentEventId) {
        Objects.requireNonNull(paymentEventId, "paymentEventId");
        return UUID.nameUUIDFromBytes(("release-reservation:" + paymentEventId).getBytes(StandardCharsets.UTF_8));
    }
    public static String desiredOrderStatus(String reason) {
        return "PAYMENT_DEADLINE_EXPIRED".equals(reason) ? "EXPIRED" : "CANCELLED";
    }
}
