package com.philia.flashsale.inventory.allocation.adapter.in.web.mapper;

import com.philia.flashsale.inventory.allocation.adapter.in.web.request.AllocateRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.request.ReasonRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.request.ReconcileRequest;
import com.philia.flashsale.inventory.allocation.adapter.in.web.response.AllocationResponse;
import com.philia.flashsale.inventory.allocation.application.command.AllocateCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.command.ReconcileCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.command.ReleaseCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.result.CampaignStockAllocationResult;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps only the internal allocation HTTP contract to application contracts. */
@Component
public class AllocationWebMapper {
    public AllocateCampaignStockCommand toCommand(AllocateRequest request) {
        return new AllocateCampaignStockCommand(
                request.requestId(), request.campaignId(), request.variantId(),
                request.quantity(), request.reason());
    }

    public ReleaseCampaignStockCommand toCommand(UUID requestId, ReasonRequest request) {
        return new ReleaseCampaignStockCommand(requestId, request.reason());
    }

    public ReconcileCampaignStockCommand toCommand(UUID requestId, ReconcileRequest request) {
        return new ReconcileCampaignStockCommand(
                requestId, request.soldQuantity(), request.returnedQuantity(), request.reason());
    }

    public AllocationResponse toResponse(CampaignStockAllocationResult result) {
        return new AllocationResponse(
                result.id(), result.requestId(), result.campaignId(), result.variantId(),
                result.allocatedQuantity(), result.soldQuantity(), result.returnedQuantity(),
                result.status());
    }
}
