package com.philia.flashsale.order.order.application.port.out;

import java.util.UUID;

/** Supplies a new identity without exposing the UUID provider to the application core. */
public interface GenerateOrderIdentityPort {
    UUID generate();
}
