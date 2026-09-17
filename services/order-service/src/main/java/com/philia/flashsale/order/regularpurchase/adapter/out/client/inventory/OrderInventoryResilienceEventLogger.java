package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import com.philia.flashsale.order.websupport.context.OrderRequestContext;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits bounded operational events for the Order-to-Inventory isolation boundary. */
public final class OrderInventoryResilienceEventLogger {

    private static final Logger LOG = LoggerFactory.getLogger(OrderInventoryResilienceEventLogger.class);
    private final ThreadLocal<String> currentTraceId = new ThreadLocal<>();

    public OrderInventoryResilienceEventLogger(CircuitBreaker circuitBreaker) {
        Objects.requireNonNull(circuitBreaker, "circuitBreaker is required")
                .getEventPublisher()
                .onStateTransition(this::transition);
    }

    /** Fixed rejection vocabulary; values are safe for logs and dashboards. */
    public enum Rejection {
        OPEN_CIRCUIT,
        BULKHEAD_FULL;

        String logValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    <T> T withTrace(String traceId, Supplier<T> operation) {
        currentTraceId.set(safeTraceId(traceId));
        try {
            return operation.get();
        } finally {
            currentTraceId.remove();
        }
    }

    private void transition(CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreaker.StateTransition transition = event.getStateTransition();
        LOG.info("order_inventory_resilience_transition traceId={} stateFrom={} stateTo={}",
                currentTrace(), fixedState(transition.getFromState()), fixedState(transition.getToState()));
    }

    void rejection(Rejection rejection, String traceId) {
        LOG.warn("order_inventory_resilience_rejection traceId={} outcome={}",
                safeTraceId(traceId), rejection.logValue());
    }

    private String safeTraceId(String traceId) {
        return OrderRequestContext.normalizeOrGenerate(traceId);
    }

    private String currentTrace() {
        String traceId = currentTraceId.get();
        return traceId == null ? OrderRequestContext.normalizeOrGenerate(null) : traceId;
    }

    private String fixedState(CircuitBreaker.State state) {
        return state == null ? "UNKNOWN" : state.name();
    }
}
