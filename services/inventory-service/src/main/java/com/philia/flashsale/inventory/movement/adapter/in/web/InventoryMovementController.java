package com.philia.flashsale.inventory.movement.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.inventory.movement.adapter.in.web.mapper.MovementWebMapper;
import com.philia.flashsale.inventory.movement.adapter.in.web.response.MovementResponse;
import com.philia.flashsale.inventory.movement.application.port.in.ListStockMovementsUseCase;
import com.philia.flashsale.inventory.movement.application.query.ListStockMovementsQuery;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryMovementController implements InventoryMovementApi {
    private final ListStockMovementsUseCase listStockMovements;
    private final MovementWebMapper mapper;

    public InventoryMovementController(
            ListStockMovementsUseCase listStockMovements,
            MovementWebMapper mapper) {
        this.listStockMovements = listStockMovements;
        this.mapper = mapper;
    }

    @Override
    @GetMapping("/{variantId}/movements")
    public ResponseEntity<ApiResponse<PageResponse<MovementResponse>>> movements(
            @PathVariable UUID variantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = listStockMovements.list(new ListStockMovementsQuery(variantId, page, size));
        return ResponseEntity.ok(ApiResponse.success(mapper.toResponse(result)));
    }
}
