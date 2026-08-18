package com.philia.flashsale.payment.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

/** Unit guards for the bounded retry policy used by the PaymentRequested listener. */
class PaymentKafkaConsumerConfigurationTests {

    @Test
    void emitsTheApprovedOneThreeTenSecondRetrySequence() {
        BackOffExecution execution = new PaymentKafkaConsumerConfiguration.PaymentKafkaRetryBackOff(
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10))).start();

        assertThat(execution.nextBackOff()).isEqualTo(1_000L);
        assertThat(execution.nextBackOff()).isEqualTo(3_000L);
        assertThat(execution.nextBackOff()).isEqualTo(10_000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void rejectsMissingOrNonPositiveRetryDelays() {
        assertThatThrownBy(() -> new PaymentKafkaConsumerConfiguration.PaymentKafkaRetryBackOff(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentKafkaConsumerConfiguration.PaymentKafkaRetryBackOff(
                List.of(Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
