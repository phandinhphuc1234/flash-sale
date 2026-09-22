package com.philia.flashsale.inventory.regularhold.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.request.CreateRegularStockHoldRequest;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.response.RegularStockHoldResponse;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Internal regular stock")
@SecurityRequirement(name = "bearerAuth")
public interface RegularStockHoldApi {
    @Operation(summary = "Create or replay a regular stock hold", description = "Order-only endpoint; all requested lines are held atomically or none are held.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Stock hold created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Equivalent hold replayed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Insufficient stock or hold identity conflict")
    })
    ResponseEntity<ApiResponse<RegularStockHoldResponse>> create(CreateRegularStockHoldRequest request);
}
