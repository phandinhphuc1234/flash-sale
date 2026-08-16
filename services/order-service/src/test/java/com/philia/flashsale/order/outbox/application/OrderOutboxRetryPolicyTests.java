package com.philia.flashsale.order.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxRetryPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OrderOutboxRetryPolicyTests {

    @Test
    void usesExponentialDelayAndCapsAtSixtySeconds() {
        var policy = new OrderOutboxRetryPolicy(Duration.ofSeconds(60));

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy.delayForAttempt(7)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.delayForAttempt(100)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void rejectsInvalidCapAndAttempt() {
        assertThatThrownBy(() -> new OrderOutboxRetryPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        var policy = new OrderOutboxRetryPolicy(Duration.ofSeconds(60));
        assertThatThrownBy(() -> policy.delayForAttempt(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
