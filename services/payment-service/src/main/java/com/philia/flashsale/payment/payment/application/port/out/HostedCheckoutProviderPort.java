package com.philia.flashsale.payment.payment.application.port.out;

import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutExpireRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;

/** Capability boundary for a hosted payment provider; no provider SDK types cross this interface. */
public interface HostedCheckoutProviderPort {

    HostedCheckoutResult create(HostedCheckoutCreateRequest request);

    HostedCheckoutResult retrieve(HostedCheckoutRetrieveRequest request);

    HostedCheckoutResult expire(HostedCheckoutExpireRequest request);
}
