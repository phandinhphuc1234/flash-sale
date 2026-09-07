package com.philia.flashsale.order.regularpurchase.adapter.out.client.product;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.ProductPurchaseQuote;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.websupport.context.OrderRequestContext;
import feign.FeignException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Product quote adapter that validates a complete, identity-compatible response at the boundary. */
@Component
public final class ProductPurchaseQuoteClientAdapter implements LoadProductPurchaseQuotesPort {

    private static final Logger LOG = LoggerFactory.getLogger(ProductPurchaseQuoteClientAdapter.class);

    private final ProductPurchaseQuoteFeignClient client;

    public ProductPurchaseQuoteClientAdapter(ProductPurchaseQuoteFeignClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public List<ProductPurchaseQuote> loadQuotes(List<UUID> variantIds, String traceId) {
        if (variantIds == null || variantIds.isEmpty() || variantIds.size() > 20
                || variantIds.stream().anyMatch(Objects::isNull)
                || variantIds.stream().distinct().count() != variantIds.size()) {
            throw new IllegalArgumentException("purchase quote variants must be one to twenty distinct IDs");
        }
        List<UUID> requested = variantIds.stream().sorted().toList();
        String safeTraceId = OrderRequestContext.normalizeOrGenerate(traceId);
        try {
            ApiResponse<ProductPurchaseQuoteBatchResponse> envelope = client.purchaseQuotes(
                    safeTraceId, new ProductPurchaseQuoteBatchRequest(requested));
            return verifyAndMap(requested, envelope);
        } catch (ProductPurchaseQuoteRemoteException exception) {
            if (exception.failure() == ProductPurchaseQuoteRemoteException.Failure.TOKEN_REJECTED) {
                LOG.warn("order_product_purchase_quote_token_rejected traceId={} status={}",
                        safeTraceId, exception.status());
            }
            throw new RegularPurchaseDownstreamException(exception.failure()
                    == ProductPurchaseQuoteRemoteException.Failure.REJECTED
                    ? RegularPurchaseDownstreamException.Failure.PRODUCT_QUOTE_REJECTED
                    : RegularPurchaseDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE);
        } catch (FeignException exception) {
            LOG.warn("order_product_purchase_quote_unavailable traceId={} failureType={}",
                    safeTraceId, exception.getClass().getSimpleName());
            throw new RegularPurchaseDownstreamException(
                    RegularPurchaseDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE);
        }
    }

    private List<ProductPurchaseQuote> verifyAndMap(List<UUID> requested,
            ApiResponse<ProductPurchaseQuoteBatchResponse> envelope) {
        List<ProductPurchaseQuoteBatchResponse.ProductPurchaseQuoteResponse> responses =
                envelope == null || !envelope.success() || envelope.data() == null
                        ? null : envelope.data().quotes();
        if (responses == null || responses.size() != requested.size()) {
            throw unavailable();
        }
        List<ProductPurchaseQuoteBatchResponse.ProductPurchaseQuoteResponse> ordered = responses.stream()
                .sorted(Comparator.comparing(ProductPurchaseQuoteBatchResponse.ProductPurchaseQuoteResponse::variantId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (int index = 0; index < requested.size(); index++) {
            var response = ordered.get(index);
            if (response.variantId() == null || !requested.get(index).equals(response.variantId())
                    || (response.found() && (!response.sellable() || response.unitPrice() == null
                    || response.currency() == null || response.productId() == null))) {
                throw unavailable();
            }
        }
        return ordered.stream().map(response -> new ProductPurchaseQuote(response.variantId(), response.found(),
                response.sellable(), response.unavailableReason(), response.productId(), response.sku(),
                response.productName(), response.variantName(), response.unitPrice() == null ? null
                        : com.philia.flashsale.order.order.domain.valueobject.Money.of(response.unitPrice()),
                response.currency(), response.catalogVersion())).toList();
    }

    private RegularPurchaseDownstreamException unavailable() {
        return new RegularPurchaseDownstreamException(
                RegularPurchaseDownstreamException.Failure.PRODUCT_SERVICE_UNAVAILABLE);
    }
}
