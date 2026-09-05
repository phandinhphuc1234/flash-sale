package com.philia.flashsale.cart.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CartCheckoutSnapshotRequest(@NotNull UUID shopperId) { }
