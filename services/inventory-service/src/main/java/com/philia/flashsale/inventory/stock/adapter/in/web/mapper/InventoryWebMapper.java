package com.philia.flashsale.inventory.stock.adapter.in.web.mapper;

import com.philia.flashsale.inventory.stock.adapter.in.web.request.AdjustStockRequest;
import com.philia.flashsale.inventory.stock.adapter.in.web.response.InventoryResponse;
import com.philia.flashsale.inventory.stock.adapter.in.web.response.InventoryListItemResponse;
import com.philia.flashsale.inventory.stock.application.command.AdjustStockCommand;
import com.philia.flashsale.inventory.stock.application.result.InventoryResult;
import com.philia.flashsale.inventory.stock.application.result.InventoryPageResult;
import com.philia.flashsale.common.web.PageResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps only the stock HTTP boundary; it contains no business decisions. */
@Component
public class InventoryWebMapper {
    public AdjustStockCommand toCommand(UUID variantId, AdjustStockRequest request) {
        return new AdjustStockCommand(
                request.requestId(), variantId, request.type(), request.quantity(), request.reason());
    }

    public InventoryResponse toResponse(InventoryResult result) {
        return new InventoryResponse(
                result.variantId(), result.skuSnapshot(), result.onHandQuantity(),
                result.campaignAllocatedQuantity(), result.availableQuantity());
    }

    public PageResponse<InventoryListItemResponse> toPageResponse(InventoryPageResult page) {
        var content = page.content().stream()
                .map(item -> new InventoryListItemResponse(
                        item.variantId(), item.skuSnapshot(), item.onHandQuantity(),
                        item.campaignAllocatedQuantity(), item.availableQuantity(), item.updatedAt()))
                .toList();
        return PageResponse.of(content, page.page(), page.size(), page.totalElements());
    }
}
