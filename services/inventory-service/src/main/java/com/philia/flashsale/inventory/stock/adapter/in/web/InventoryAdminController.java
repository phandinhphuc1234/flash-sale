package com.philia.flashsale.inventory.stock.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.inventory.stock.adapter.in.web.mapper.InventoryWebMapper;
import com.philia.flashsale.inventory.stock.adapter.in.web.request.AdjustStockRequest;
import com.philia.flashsale.inventory.stock.adapter.in.web.response.InventoryResponse;
import com.philia.flashsale.inventory.stock.application.port.in.AdjustStockUseCase;
import com.philia.flashsale.inventory.stock.application.port.in.GetInventoryUseCase;
import com.philia.flashsale.inventory.stock.application.query.GetInventoryQuery;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryAdminController implements InventoryAdminApi {
    private final GetInventoryUseCase getInventory;
    private final AdjustStockUseCase adjustStock;
    private final InventoryWebMapper mapper;

    public InventoryAdminController(
            GetInventoryUseCase getInventory,
            AdjustStockUseCase adjustStock,
            InventoryWebMapper mapper) {
        this.getInventory = getInventory;
        this.adjustStock = adjustStock;
        this.mapper = mapper;
    }

    @GetMapping("/{variantId}")
    @Override
    public ResponseEntity<ApiResponse<InventoryResponse>> get(@PathVariable UUID variantId) {
        var result = getInventory.get(new GetInventoryQuery(variantId));
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(result)));
    }

    @PostMapping("/{variantId}/adjustments")
    @Override
    public ResponseEntity<ApiResponse<InventoryResponse>> adjust(
            @PathVariable UUID variantId,
            @Valid @RequestBody AdjustStockRequest request) {
        var result = adjustStock.adjust(mapper.toCommand(variantId, request));
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted", mapper.toResponse(result)));
    }
}
