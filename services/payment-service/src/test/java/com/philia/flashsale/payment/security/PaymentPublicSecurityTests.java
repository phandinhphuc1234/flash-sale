package com.philia.flashsale.payment.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.payment.payment.application.model.StartCheckoutCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Boundary-level checks that callers cannot smuggle an invalid or non-printable idempotency key. */
class PaymentPublicSecurityTests {
    @Test
    void checkoutCommandRejectsControlCharactersAndOversizedKeys() {
        UUID payment = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> new StartCheckoutCommand(payment, user, "bad\nkey"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StartCheckoutCommand(payment, user, "x".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
