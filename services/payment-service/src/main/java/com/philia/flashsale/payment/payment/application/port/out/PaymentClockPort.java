package com.philia.flashsale.payment.payment.application.port.out;

import java.time.Instant;

/** Application-owned time capability so domain behavior remains deterministic in tests. */
public interface PaymentClockPort {

    Instant now();
}
