package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHold;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.websupport.context.OrderRequestContext;
import feign.FeignException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Inventory hold adapter preserves command identity and exposes a timeout as an explicit ambiguity. */
@Component
public final class InventoryRegularHoldClientAdapter implements CreateRegularStockHoldPort {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryRegularHoldClientAdapter.class);

    private final InventoryRegularHoldFeignClient client;

    public InventoryRegularHoldClientAdapter(InventoryRegularHoldFeignClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public RegularStockHold create(RegularStockHoldCommand command, String traceId) {
        Objects.requireNonNull(command, "command is required");
        String safeTraceId = OrderRequestContext.normalizeOrGenerate(traceId);
        try {
            ApiResponse<InventoryRegularHoldResponse> envelope = client.create(safeTraceId, toRequest(command));
            return verifyAndMap(command, envelope);
        } catch (InventoryRegularHoldRemoteException exception) {
            if (exception.failure() == InventoryRegularHoldRemoteException.Failure.TOKEN_REJECTED) {
                LOG.warn("order_inventory_regular_hold_token_rejected traceId={} status={}",
                        safeTraceId, exception.status());
            }
            throw new RegularPurchaseDownstreamException(mapFailure(exception.failure()));
        } catch (FeignException exception) {
            LOG.warn("order_inventory_regular_hold_ambiguous traceId={} failureType={}",
                    safeTraceId, exception.getClass().getSimpleName());
            throw new RegularPurchaseDownstreamException(
                    RegularPurchaseDownstreamException.Failure.INVENTORY_HOLD_AMBIGUOUS);
        }
    }

    private InventoryRegularHoldRequest toRequest(RegularStockHoldCommand command) {
        return new InventoryRegularHoldRequest(command.holdId(), command.purchaseRequestId(), command.orderId(),
                command.shopperId(), command.requestedAt(), command.items().stream()
                        .map(item -> new InventoryRegularHoldRequest.InventoryRegularHoldItemRequest(
                                item.variantId(), item.quantity()))
                        .toList());
    }

    private RegularStockHold verifyAndMap(RegularStockHoldCommand command,
            ApiResponse<InventoryRegularHoldResponse> envelope) {
        InventoryRegularHoldResponse response = envelope == null || !envelope.success() ? null : envelope.data();
        if (response == null || !command.holdId().equals(response.holdId())
                || !command.purchaseRequestId().equals(response.purchaseRequestId())
                || !command.orderId().equals(response.orderId()) || !"HELD".equals(response.status())
                || response.expiresAt() == null || !response.expiresAt().isAfter(command.requestedAt())
                || response.items() == null || response.items().size() != command.items().size()) {
            throw unavailable();
        }
        List<RegularStockHold.RegularStockHoldItem> items = response.items().stream()
                .map(item -> new RegularStockHold.RegularStockHoldItem(item.variantId(), item.quantity()))
                .toList();
        boolean sameItems = command.items().stream().allMatch(expected -> items.stream().anyMatch(actual ->
                expected.variantId().equals(actual.variantId()) && expected.quantity() == actual.quantity()));
        if (!sameItems) throw unavailable();
        return new RegularStockHold(response.holdId(), response.purchaseRequestId(), response.orderId(),
                response.status(), response.expiresAt(), items);
    }

    private RegularPurchaseDownstreamException.Failure mapFailure(InventoryRegularHoldRemoteException.Failure failure) {
        return switch (failure) {
            case INSUFFICIENT_STOCK -> RegularPurchaseDownstreamException.Failure.INVENTORY_INSUFFICIENT_STOCK;
            case ITEM_NOT_FOUND -> RegularPurchaseDownstreamException.Failure.INVENTORY_ITEM_NOT_FOUND;
            case IDENTITY_CONFLICT -> RegularPurchaseDownstreamException.Failure.INVENTORY_HOLD_CONFLICT;
            case REJECTED -> RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE;
            case TOKEN_REJECTED, UNAVAILABLE -> RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE;
        };
    }

    private RegularPurchaseDownstreamException unavailable() {
        return new RegularPurchaseDownstreamException(
                RegularPurchaseDownstreamException.Failure.INVENTORY_SERVICE_UNAVAILABLE);
    }
}
