package com.philia.flashsale.cart.application.query;

import java.util.UUID;

/** Trusted internal query; the HTTP adapter obtains the caller's exact machine identity separately. */
public record GetCartCheckoutSnapshotQuery(UUID shopperId) { }
