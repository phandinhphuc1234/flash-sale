package com.philia.flashsale.order.regularpurchase.adapter.in.scheduling;

import com.philia.flashsale.order.configuration.RegularPurchaseRecoveryProperties;
import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutLine;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseRecoveryClaim;
import com.philia.flashsale.order.regularpurchase.application.port.out.ClaimRegularPurchaseRecoveryPort;
import com.philia.flashsale.order.regularpurchase.application.usecase.RegularPurchaseCheckoutService;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import com.philia.flashsale.order.observability.OrderObservability;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;

/**
 * Resumes stale regular-purchase checkpoints with their persisted identities.
 *
 * <p>The lease transaction is deliberately separate from downstream HTTP. A transient exception
 * leaves the lease until it expires, while a completed/rejected attempt releases it immediately.
 * The normal checkout use case remains the single orchestration path.</p>
 */
@Component
@ConditionalOnProperty(name = "order.regular-purchase.runtime.recovery-enabled", havingValue = "true")
public final class RegularPurchaseRecoveryJob {
    private static final Logger LOG = LoggerFactory.getLogger(RegularPurchaseRecoveryJob.class);

    private final RegularPurchaseCheckoutService checkout;
    private final ClaimRegularPurchaseRecoveryPort claims;
    private final RegularPurchaseRecoveryProperties properties;
    private final Clock clock;
    private final String workerId;
    private final OrderObservability observability;

    @Autowired
    public RegularPurchaseRecoveryJob(RegularPurchaseCheckoutService checkout,
            ClaimRegularPurchaseRecoveryPort claims, RegularPurchaseRecoveryProperties properties,
            Clock clock, OrderObservability observability) {
        this(checkout, claims, properties, clock, defaultWorkerId(), observability);
    }

    public RegularPurchaseRecoveryJob(RegularPurchaseCheckoutService checkout,
            ClaimRegularPurchaseRecoveryPort claims, RegularPurchaseRecoveryProperties properties,
            Clock clock) {
        this(checkout, claims, properties, clock, defaultWorkerId(), OrderObservability.noop());
    }

    RegularPurchaseRecoveryJob(RegularPurchaseCheckoutService checkout,
            ClaimRegularPurchaseRecoveryPort claims, RegularPurchaseRecoveryProperties properties,
            Clock clock, String workerId) {
        this(checkout, claims, properties, clock, workerId, OrderObservability.noop());
    }

    RegularPurchaseRecoveryJob(RegularPurchaseCheckoutService checkout,
            ClaimRegularPurchaseRecoveryPort claims, RegularPurchaseRecoveryProperties properties,
            Clock clock, String workerId, OrderObservability observability) {
        this.checkout = Objects.requireNonNull(checkout, "checkout");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    @Scheduled(fixedDelayString = "${order.regular-purchase.recovery.poll-interval:1s}")
    public void recoverDue() {
        Instant now = clock.instant();
        List<RegularPurchaseRecoveryClaim> batch;
        try {
            batch = claims.claim(workerId, now, properties.batchSize(), properties.claimLease());
            observability.recordRegularRecovery("claim", batch.isEmpty() ? "empty" : "claimed");
        } catch (RuntimeException exception) {
            observability.recordRegularRecovery("claim", "error");
            throw exception;
        }
        for (RegularPurchaseRecoveryClaim claim : batch) {
            recoverOne(claim);
        }
    }

    private void recoverOne(RegularPurchaseRecoveryClaim claim) {
        RegularPurchaseRequest request = claim.request();
        try {
            if (request.source() == PurchaseSource.BUY_NOW) {
                var line = request.lines().getFirst();
                checkout.checkout(new BuyNowCheckoutCommand(request.shopperId(), request.idempotencyKey(),
                        line.variantId(), line.quantity(), line.expectedUnitPrice(), line.currency(),
                        recoveryTraceId(request.id()), null, null));
            } else {
                List<CartCheckoutLine> lines = request.lines().stream()
                        .map(line -> new CartCheckoutLine(line.variantId(), line.quantity(), line.cartItemVersion(),
                                line.expectedUnitPrice(), line.currency()))
                        .toList();
                checkout.checkout(new CartCheckoutCommand(request.shopperId(), request.idempotencyKey(),
                        request.submittedCartVersion(), lines, recoveryTraceId(request.id()), null, null));
            }
            claims.release(request.id(), claim.workerId());
            observability.recordRegularRecovery("release", "released");
        } catch (RuntimeException exception) {
            // The persisted lease bounds retries; do not clear it on a transient downstream failure.
            LOG.warn("regular_purchase_recovery_deferred state={} failureType={}", request.state(),
                    exception.getClass().getSimpleName());
            observability.recordRegularRecovery("checkout", "deferred");
        }
    }

    private String recoveryTraceId(UUID requestId) {
        return "regular-recovery-" + requestId;
    }

    private static String defaultWorkerId() {
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || hostname.isBlank() ? "order-regular-purchase-recovery" : hostname;
    }

}
