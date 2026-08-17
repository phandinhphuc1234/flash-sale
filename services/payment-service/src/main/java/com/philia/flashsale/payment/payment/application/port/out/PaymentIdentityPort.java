package com.philia.flashsale.payment.payment.application.port.out;

import java.util.UUID;

/** Application-owned identity generation capability. */
public interface PaymentIdentityPort {

    UUID newId();
}
