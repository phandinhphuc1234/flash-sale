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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Internal Order-only Cart snapshot adapter; never routed through the public Gateway. */
@RestController
@RequestMapping("/internal/v1/cart-checkout-snapshots")
@ConditionalOnProperty(name = "cart.persistence.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Internal cart", description = "Private Order-to-Cart checkout snapshot contract")
@SecurityRequirement(name = "bearerAuth")
public class CartCheckoutSnapshotController {
    private final GetCartCheckoutSnapshotUseCase snapshot;
    private final CartCheckoutSnapshotMapper mapper;

    public CartCheckoutSnapshotController(GetCartCheckoutSnapshotUseCase snapshot,
            CartCheckoutSnapshotMapper mapper) {
        this.snapshot = snapshot;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Read an owned cart checkout snapshot", description = "Internal Order-only endpoint; the shopper owner comes from the service-authenticated request.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cart snapshot returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Cart does not exist"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Cart is empty")
    })
    public ResponseEntity<ApiResponse<CartCheckoutSnapshotResponse>> get(
            @Valid @RequestBody CartCheckoutSnapshotRequest request) {
        var result = snapshot.get(new GetCartCheckoutSnapshotQuery(request.shopperId()));
        return ResponseEntity.ok(ApiResponse.success("Cart checkout snapshot retrieved", mapper.toResponse(result)));
    }
}
