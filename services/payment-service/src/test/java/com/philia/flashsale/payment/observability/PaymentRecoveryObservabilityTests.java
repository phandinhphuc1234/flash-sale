package com.philia.flashsale.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaymentRecoveryObservabilityTests {

    @Test
    void usesOnlyBoundedLabelsAndRecordsRecoverySignals() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentRecoveryObservability observability = new PaymentRecoveryObservability(registry);

        observability.recordOutcome("CREATE_SESSION", ReconcilePaymentResult.Outcome.DEFERRED);
        observability.recordOutcome("provider-session-cs-secret", ReconcilePaymentResult.Outcome.MANUAL_REVIEW);
        observability.recordAttempt("REFRESH_SESSION", 2);
        observability.recordAge("EXPIRE_SESSION", Duration.ofSeconds(3));
        observability.recordQueueSize(4);

        assertThat(registry.get("payment.recovery.outcomes.total").tag("work_type", "create_session")
                .tag("outcome", "deferred").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.recovery.outcomes.total").tag("work_type", "unknown")
                .tag("outcome", "manual_review").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.recovery.attempts.total").tag("work_type", "refresh_session")
                .counter().count()).isEqualTo(2);
        assertThat(registry.get("payment.recovery.queue.size").gauge().value()).isEqualTo(4);
    }
}
