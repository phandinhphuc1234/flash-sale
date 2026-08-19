package com.philia.flashsale.payment.payment.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.philia.flashsale.payment.observability.PaymentRecoveryObservability;
import com.philia.flashsale.payment.payment.application.port.in.ReconcilePaymentUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentRecoveryJobWiringTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PaymentRecoveryJob.class)
            .withBean(ReconcilePaymentUseCase.class, () -> mock(ReconcilePaymentUseCase.class))
            .withBean(PaymentRecoveryObservability.class, PaymentRecoveryObservability::noop)
            .withPropertyValues(
                    "payment.acceptance.enabled=true",
                    "payment.checkout.enabled=true",
                    "payment.stripe.enabled=true",
                    "payment.recovery.enabled=true");

    @Test
    void wiresTheRecoveryJobThroughItsRuntimeConstructor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PaymentRecoveryJob.class);
        });
    }
}
