package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Infrastructure decorator that contains repeated Inventory outages without inventing a hold.
 *
 * <p>The original command and trace are passed to the Feign translator exactly once when the
 * circuit admits the call. Business rejections remain truthful and do not affect circuit state.
 * An open circuit maps only to the existing recoverable unavailable outcome; there is no retry or
 * fallback stock decision here.</p>
 */
public final class ResilientInventoryRegularHoldClientAdapter implements CreateRegularStockHoldPort {

    private final CreateRegularStockHoldPort delegate;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final OrderInventoryResilienceEventLogger eventLogger;

    public ResilientInventoryRegularHoldClientAdapter(CreateRegularStockHoldPort delegate,
            CircuitBreaker circuitBreaker, Bulkhead bulkhead,
            OrderInventoryResilienceEventLogger eventLogger) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker is required");
        this.bulkhead = Objects.requireNonNull(bulkhead, "bulkhead is required");
        this.eventLogger = Objects.requireNonNull(eventLogger, "eventLogger is required");
    }

    @Override
    public RegularStockHold create(RegularStockHoldCommand command, String traceId) {
        Objects.requireNonNull(command, "command is required");
        Supplier<RegularStockHold> circuitGuardedCall = CircuitBreaker.decorateSupplier(
                circuitBreaker, () -> delegate.create(command, traceId));
        Supplier<RegularStockHold> guardedCall = Bulkhead.decorateSupplier(bulkhead, circuitGuardedCall);
        try {
            return eventLogger.withTrace(traceId, guardedCall);
        } catch (CallNotPermittedException exception) {
            eventLogger.rejection(OrderInventoryResilienceEventLogger.Rejection.OPEN_CIRCUIT, traceId);
            throw unavailable();
        } catch (BulkheadFullException exception) {
            eventLogger.rejection(OrderInventoryResilienceEventLogger.Rejection.BULKHEAD_FULL, traceId);
            throw unavailable();
        }
    }

    private RegularPurchaseDownstreamException unavailable() {
        return new RegularPurchaseDownstreamException(
                RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
    }

    static boolean isBusinessFailure(Throwable throwable) {
        if (!(throwable instanceof RegularPurchaseDownstreamException exception)) {
            return false;
        }
        return switch (exception.failure()) {
            case INVENTORY_INSUFFICIENT_STOCK, INVENTORY_ITEM_NOT_FOUND, INVENTORY_HOLD_CONFLICT -> true;
            default -> false;
        };
    }
}
