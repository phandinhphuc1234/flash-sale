package com.philia.flashsale.payment.payment.application.port.in;

import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentByOrderQuery;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentQuery;
import com.philia.flashsale.payment.payment.application.model.query.PaymentDetailsResult;

/** Inbound capability for owner-scoped Payment reads. */
public interface GetOwnedPaymentUseCase {
    PaymentDetailsResult get(GetOwnedPaymentQuery query);

    PaymentDetailsResult getByOrder(GetOwnedPaymentByOrderQuery query);
}
