package com.philia.flashsale.inventory.allocation.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.inventory.allocation.adapter.in.web.request.AllocateRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.request.ReasonRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.request.ReconcileRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.response.AllocationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** OpenAPI documentation contract for the internal allocation HTTP adapter. */
@Tag(name = "Internal allocation")
@SecurityRequirement(name = "bearerAuth")
public interface InventoryInternalApi {

    @Operation(
            summary = "Allocate variant stock to a campaign",
            description = "Internal endpoint; caller needs SCOPE_INVENTORY_WRITE."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Allocation accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid allocation request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired service JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "JWT does not have SCOPE_INVENTORY_WRITE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Inventory item does not exist"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Insufficient stock, duplicate allocation, or idempotency conflict")
    })
    ResponseEntity<ApiResponse<AllocationResponse>> allocate(AllocateRequest request);

    @Operation(
            summary = "Release an active campaign allocation",
            description = "Internal endpoint owned by the Campaign Service caller."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Allocation released"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid release request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired service JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "JWT does not have SCOPE_INVENTORY_WRITE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Allocation does not exist"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Allocation is not active")
    })
    ResponseEntity<ApiResponse<AllocationResponse>> release(UUID requestId, ReasonRequest request);

    @Operation(
            summary = "Reconcile sold and returned campaign stock",
            description = "soldQuantity plus returnedQuantity must equal allocated quantity."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Allocation reconciled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid reconciliation request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing, invalid, or expired service JWT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "JWT does not have SCOPE_INVENTORY_WRITE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Allocation does not exist"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Allocation is not active or reconciliation does not balance")
    })
    ResponseEntity<ApiResponse<AllocationResponse>> reconcile(UUID requestId, ReconcileRequest request);
}
