package com.philia.flashsale.inventory.stock.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.inventory.stock.adapter.in.web.request.AdjustStockRequest;
import com.philia.flashsale.inventory.stock.adapter.in.web.response.InventoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** OpenAPI documentation contract for the admin Inventory HTTP adapter. */
@Tag(name = "Admin inventory")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryAdminApi {

    @Operation(summary = "Read the current inventory state for one product variant")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Inventory state returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "JWT does not have INVENTORY_ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Inventory item does not exist")
    })
    ResponseEntity<ApiResponse<InventoryResponse>> get(UUID variantId);

    @Operation(
            summary = "Adjust physical on-hand stock",
            description = "requestId provides idempotent command replay."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Adjusted inventory state returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid adjustment request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "JWT does not have INVENTORY_ADMIN"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Inventory item does not exist"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Stock or idempotency rule rejected the command")
    })
    ResponseEntity<ApiResponse<InventoryResponse>> adjust(UUID variantId, AdjustStockRequest request);

}
