package com.philia.flashsale.inventory.regularhold.adapter.in.web.mapper;

import com.philia.flashsale.inventory.regularhold.adapter.in.web.request.CreateRegularStockHoldRequest;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.response.RegularStockHoldItemResponse;
import com.philia.flashsale.inventory.regularhold.adapter.in.web.response.RegularStockHoldResponse;
import com.philia.flashsale.inventory.regularhold.application.command.CreateRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.command.RegularStockHoldLine;
import com.philia.flashsale.inventory.regularhold.application.result.RegularStockHoldResult;
import org.springframework.stereotype.Component;

@Component
public class RegularStockHoldWebMapper {
    public CreateRegularStockHoldCommand toCommand(CreateRegularStockHoldRequest request) {
        return new CreateRegularStockHoldCommand(request.holdId(), request.purchaseRequestId(),
                request.orderId(), request.shopperId(), request.requestedAt(), request.items().stream()
                        .map(item -> new RegularStockHoldLine(item.variantId(), item.quantity()))
                        .toList());
    }

    public RegularStockHoldResponse toResponse(RegularStockHoldResult result) {
        return new RegularStockHoldResponse(result.holdId(), result.purchaseRequestId(), result.orderId(),
                result.status(), result.expiresAt(), result.items().stream()
                        .map(item -> new RegularStockHoldItemResponse(item.variantId(), item.quantity()))
                        .toList());
    }
}
