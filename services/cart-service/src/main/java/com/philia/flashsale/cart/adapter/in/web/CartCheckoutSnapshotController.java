package com.philia.flashsale.cart.adapter.in.web;

import com.philia.flashsale.cart.application.port.in.GetCartCheckoutSnapshotUseCase;
import com.philia.flashsale.cart.application.query.GetCartCheckoutSnapshotQuery;
import com.philia.flashsale.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal Order-only Cart snapshot adapter; never routed through the public Gateway. */
@RestController
@RequestMapping("/internal/v1/cart-checkout-snapshots")
@ConditionalOnProperty(name = "cart.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class CartCheckoutSnapshotController {
    private final GetCartCheckoutSnapshotUseCase snapshot;
    private final CartCheckoutSnapshotMapper mapper;

    public CartCheckoutSnapshotController(GetCartCheckoutSnapshotUseCase snapshot,
            CartCheckoutSnapshotMapper mapper) {
        this.snapshot = snapshot;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CartCheckoutSnapshotResponse>> get(
            @Valid @RequestBody CartCheckoutSnapshotRequest request) {
        var result = snapshot.get(new GetCartCheckoutSnapshotQuery(request.shopperId()));
        return ResponseEntity.ok(ApiResponse.success("Cart checkout snapshot retrieved", mapper.toResponse(result)));
    }
}
