package com.philia.flashsale.order.regularpurchase.adapter.out.client.cart;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.CartCheckoutSnapshot;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadCartCheckoutSnapshotPort;
import com.philia.flashsale.order.websupport.context.OrderRequestContext;
import feign.FeignException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class CartCheckoutSnapshotClientAdapter implements LoadCartCheckoutSnapshotPort {
    private final CartCheckoutSnapshotFeignClient client;

    public CartCheckoutSnapshotClientAdapter(CartCheckoutSnapshotFeignClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public CartCheckoutSnapshot load(UUID shopperId, String traceId) {
        Objects.requireNonNull(shopperId, "shopperId");
        try {
            ApiResponse<CartCheckoutSnapshotWireModels.Response> envelope = client.snapshot(
                    OrderRequestContext.normalizeOrGenerate(traceId),
                    new CartCheckoutSnapshotWireModels.Request(shopperId));
            var response = envelope == null || !envelope.success() ? null : envelope.data();
            if (response == null || !shopperId.equals(response.ownerId()) || response.cartId() == null
                    || response.cartVersion() < 0 || response.items() == null || response.items().isEmpty()
                    || response.items().stream().anyMatch(item -> item == null || item.variantId() == null
                            || item.quantity() < 1 || item.quantity() > 10 || item.itemVersion() <= 0)
                    || response.items().stream().map(CartCheckoutSnapshotWireModels.Item::variantId).distinct().count()
                    != response.items().size()) {
                throw unavailable();
            }
            return new CartCheckoutSnapshot(response.cartId(), response.ownerId(), response.cartVersion(),
                    response.items().stream().map(item -> new CartCheckoutSnapshot.CartCheckoutSnapshotItem(
                            item.variantId(), item.quantity(), item.itemVersion())).toList());
        } catch (CartCheckoutSnapshotRemoteException exception) {
            throw unavailable();
        } catch (FeignException exception) {
            throw unavailable();
        }
    }

    private RegularPurchaseDownstreamException unavailable() {
        return new RegularPurchaseDownstreamException(
                RegularPurchaseDownstreamException.Failure.CART_SERVICE_UNAVAILABLE);
    }
}
