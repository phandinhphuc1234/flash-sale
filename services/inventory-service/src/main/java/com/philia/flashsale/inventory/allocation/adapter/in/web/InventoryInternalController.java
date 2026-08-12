package com.philia.flashsale.inventory.allocation.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.inventory.allocation.adapter.in.web.mapper.AllocationWebMapper;
import com.philia.flashsale.inventory.allocation.adapter.in.web.request.AllocateRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.request.ReasonRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.request.ReconcileRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.response.AllocationResponse;
import com.philia.flashsale.inventory.allocation.application.port.in.AllocateCampaignStockUseCase;
import com.philia.flashsale.inventory.allocation.application.port.in.ReconcileCampaignStockUseCase;
import com.philia.flashsale.inventory.allocation.application.port.in.ReleaseCampaignStockUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/campaign-stock-allocations")
public class InventoryInternalController implements InventoryInternalApi {
    private final AllocateCampaignStockUseCase allocateCampaignStock;
    private final ReleaseCampaignStockUseCase releaseCampaignStock;
    private final ReconcileCampaignStockUseCase reconcileCampaignStock;
    private final AllocationWebMapper mapper;

    public InventoryInternalController(
            AllocateCampaignStockUseCase allocateCampaignStock,
            ReleaseCampaignStockUseCase releaseCampaignStock,
            ReconcileCampaignStockUseCase reconcileCampaignStock,
            AllocationWebMapper mapper) {
        this.allocateCampaignStock = allocateCampaignStock;
        this.releaseCampaignStock = releaseCampaignStock;
        this.reconcileCampaignStock = reconcileCampaignStock;
        this.mapper = mapper;
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<AllocationResponse>> allocate(
            @Valid @RequestBody AllocateRequest request) {
        var result = allocateCampaignStock.allocate(mapper.toCommand(request));
        return ResponseEntity.ok(ApiResponse.success("Allocation accepted", mapper.toResponse(result)));
    }
    @PostMapping("/{requestId}/release")
    @Override
    public ResponseEntity<ApiResponse<AllocationResponse>> release(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReasonRequest request) {
        var result = releaseCampaignStock.release(mapper.toCommand(requestId, request));
        return ResponseEntity.ok(ApiResponse.success("Allocation released", mapper.toResponse(result)));
    }
    @PostMapping("/{requestId}/reconcile")
    @Override
    public ResponseEntity<ApiResponse<AllocationResponse>> reconcile(
            @PathVariable UUID requestId,
            @Valid @RequestBody ReconcileRequest request) {
        var result = reconcileCampaignStock.reconcile(mapper.toCommand(requestId, request));
        return ResponseEntity.ok(ApiResponse.success("Allocation reconciled", mapper.toResponse(result)));
    }
}
