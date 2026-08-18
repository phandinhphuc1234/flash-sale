package com.philia.flashsale.payment.payment.application.port.out;

import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentByOrderQuery;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentQuery;
import com.philia.flashsale.payment.payment.application.model.query.PaymentDetailsResult;
import java.util.Optional;

/** PostgreSQL-backed owner-scoped Payment read capability. */
public interface LoadOwnedPaymentPort {
    Optional<PaymentDetailsResult> load(GetOwnedPaymentQuery query);

    Optional<PaymentDetailsResult> loadByOrder(GetOwnedPaymentByOrderQuery query);
}
