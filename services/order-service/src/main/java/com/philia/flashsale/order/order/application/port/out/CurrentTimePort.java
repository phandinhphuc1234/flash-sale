package com.philia.flashsale.order.order.application.port.out;

import java.time.Instant;

/** Supplies the application commit clock. */
public interface CurrentTimePort {
    Instant now();
}
