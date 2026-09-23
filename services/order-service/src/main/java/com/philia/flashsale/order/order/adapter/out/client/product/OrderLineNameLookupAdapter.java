package com.philia.flashsale.order.order.adapter.out.client.product;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.order.order.application.model.OrderLineNames;
import com.philia.flashsale.order.order.application.port.out.LookupOrderLineNamesPort;
import com.philia.flashsale.order.regularpurchase.adapter.out.client.product.ProductPurchaseQuoteBatchRequest;
import com.philia.flashsale.order.regularpurchase.adapter.out.client.product.ProductPurchaseQuoteBatchResponse;
import com.philia.flashsale.order.regularpurchase.adapter.out.client.product.ProductPurchaseQuoteFeignClient;
import com.philia.flashsale.order.websupport.context.OrderRequestContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Keeps optional Product display metadata from becoming a Flash Sale saga dependency. */
@Component
public final class OrderLineNameLookupAdapter implements LookupOrderLineNamesPort {

    private static final Logger LOG = LoggerFactory.getLogger(OrderLineNameLookupAdapter.class);

    private final ProductPurchaseQuoteFeignClient client;

    public OrderLineNameLookupAdapter(ProductPurchaseQuoteFeignClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public Optional<OrderLineNames> lookup(UUID variantId, String traceId) {
        Objects.requireNonNull(variantId, "variantId");
        String safeTraceId = OrderRequestContext.normalizeOrGenerate(traceId);
        try {
            ApiResponse<ProductPurchaseQuoteBatchResponse> envelope = client.purchaseQuotes(
                    safeTraceId, new ProductPurchaseQuoteBatchRequest(List.of(variantId)));
            return mapTrusted(variantId, envelope);
        } catch (RuntimeException exception) {
            LOG.warn("order_line_name_lookup_unavailable traceId={} failureType={}",
                    safeTraceId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<OrderLineNames> mapTrusted(UUID requestedVariantId,
            ApiResponse<ProductPurchaseQuoteBatchResponse> envelope) {
        if (envelope == null || !envelope.success() || envelope.data() == null
                || envelope.data().quotes() == null || envelope.data().quotes().size() != 1) {
            return Optional.empty();
        }
        var quote = envelope.data().quotes().getFirst();
        if (!requestedVariantId.equals(quote.variantId()) || !quote.found() || !quote.sellable()) {
            return Optional.empty();
        }
        String productName = trustedName(quote.productName());
        String variantName = trustedName(quote.variantName());
        if (productName == null && variantName == null) return Optional.empty();
        return Optional.of(new OrderLineNames(productName, variantName));
    }

    private String trustedName(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() || normalized.length() > 255 ? null : normalized;
    }
}
