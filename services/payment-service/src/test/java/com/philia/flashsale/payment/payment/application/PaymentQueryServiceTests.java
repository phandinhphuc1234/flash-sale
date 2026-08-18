package com.philia.flashsale.payment.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.payment.payment.application.exception.PaymentNotFoundException;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentByOrderQuery;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentQuery;
import com.philia.flashsale.payment.payment.application.model.query.PaymentDetailsResult;
import com.philia.flashsale.payment.payment.application.port.out.LoadOwnedPaymentPort;
import com.philia.flashsale.payment.payment.application.service.PaymentQueryService;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Application proof that owner masking and provider-independent reads are deterministic. */
class PaymentQueryServiceTests {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();
    private static final PaymentDetailsResult RESULT = new PaymentDetailsResult(PAYMENT, ORDER,
            new BigDecimal("250000.0000"), "VND", PaymentStatus.PROCESSING,
            Instant.parse("2026-08-17T15:30:00Z"), 1, null,
            Instant.parse("2026-08-17T15:24:58Z"), Instant.parse("2026-08-17T15:25:01Z"));

    @Test
    void loadsByPaymentIdForTheTrustedOwner() {
        PaymentQueryService service = new PaymentQueryService(new FakePort(RESULT));

        assertThat(service.get(new GetOwnedPaymentQuery(PAYMENT, OWNER))).isEqualTo(RESULT);
    }

    @Test
    void loadsByOrderIdForTheTrustedOwner() {
        PaymentQueryService service = new PaymentQueryService(new FakePort(RESULT));

        assertThat(service.getByOrder(new GetOwnedPaymentByOrderQuery(ORDER, OWNER))).isEqualTo(RESULT);
    }

    @Test
    void absentAndForeignRowsUseTheSameNonEnumeratingException() {
        PaymentQueryService service = new PaymentQueryService(new FakePort(null));

        assertThatThrownBy(() -> service.get(new GetOwnedPaymentQuery(PAYMENT, OWNER)))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found");
        assertThatThrownBy(() -> service.getByOrder(new GetOwnedPaymentByOrderQuery(ORDER, OWNER)))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found");
    }

    @Test
    void everyPublicLifecycleStatusIsReturnedWithoutProviderLookup() {
        for (PaymentStatus status : PaymentStatus.values()) {
            PaymentDetailsResult result = new PaymentDetailsResult(PAYMENT, ORDER, RESULT.amount(),
                    RESULT.currency(), status, RESULT.paymentDeadline(), 3, RESULT.failureReason(),
                    RESULT.createdAt(), RESULT.updatedAt());
            assertThat(new PaymentQueryService(new FakePort(result))
                    .get(new GetOwnedPaymentQuery(PAYMENT, OWNER)).status()).isEqualTo(status);
        }
    }

    private static final class FakePort implements LoadOwnedPaymentPort {
        private final PaymentDetailsResult result;

        private FakePort(PaymentDetailsResult result) {
            this.result = result;
        }

        @Override
        public Optional<PaymentDetailsResult> load(GetOwnedPaymentQuery query) {
            return Optional.ofNullable(result);
        }

        @Override
        public Optional<PaymentDetailsResult> loadByOrder(GetOwnedPaymentByOrderQuery query) {
            return Optional.ofNullable(result);
        }
    }
}
