package com.philia.flashsale.inventory.movement.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.inventory.movement.adapter.in.web.response.MovementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** OpenAPI contract for immutable Inventory movement history. */
@Tag(name = "Admin inventory")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryMovementApi {
    @Operation(
            summary = "List immutable stock movements",
            description = "Results are ordered by createdAt DESC, then id DESC."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of immutable stock movements returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid page or size parameter"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "JWT does not have INVENTORY_ADMIN")
    })
    ResponseEntity<ApiResponse<PageResponse<MovementResponse>>> movements(
            UUID variantId, int page, int size);
}
