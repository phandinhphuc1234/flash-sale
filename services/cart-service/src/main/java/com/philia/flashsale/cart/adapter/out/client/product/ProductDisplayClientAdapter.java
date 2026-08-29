package com.philia.flashsale.cart.adapter.out.client.product;

import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException;
import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException.Failure;
import com.philia.flashsale.cart.application.port.out.LoadProductDisplaysPort;
import com.philia.flashsale.cart.application.result.ProductDisplay;
import com.philia.flashsale.cart.application.result.ProductDisplayBatch;
import com.philia.flashsale.cart.configuration.CartProductServiceTokenException;
import com.philia.flashsale.cart.configuration.CartProductServiceTokenManager;
import feign.FeignException;
import feign.RetryableException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Outbound Product adapter owning token, trace, wire mapping, identity, and failure policy. */
@Component
public final class ProductDisplayClientAdapter implements LoadProductDisplaysPort {

    private static final Logger LOG = LoggerFactory.getLogger(ProductDisplayClientAdapter.class);
    private static final int MAX_TRACE_LENGTH = 128;

    private final ProductDisplayFeignClient client;
    private final CartProductServiceTokenManager tokenManager;

    public ProductDisplayClientAdapter(
            ProductDisplayFeignClient client,
            CartProductServiceTokenManager tokenManager) {
        this.client = Objects.requireNonNull(client);
        this.tokenManager = Objects.requireNonNull(tokenManager);
    }

    @Override
    public ProductDisplayBatch load(List<UUID> variantIds, String traceId) {
        Objects.requireNonNull(variantIds, "variantIds is required");
        if (variantIds.isEmpty()) {
            return ProductDisplayBatch.available(List.of());
        }
        List<UUID> normalizedIds = normalizeVariantIds(variantIds);
        String safeTraceId = normalizeTraceId(traceId);
        try {
            ProductDisplayWireModels.Envelope envelope = client.displayDetails(
                    tokenManager.authorizationHeader(),
                    safeTraceId,
                    new ProductDisplayWireModels.Request(normalizedIds));
            return mapAndValidate(normalizedIds, envelope);
        } catch (ProductDisplayRemoteException exception) {
            LOG.warn("cart_product_display_unavailable traceId={} failureType={}",
                    safeTraceId, exception.failure());
            throw new ProductDisplayDependencyException(mapFailure(exception.failure()));
        } catch (CartProductServiceTokenException exception) {
            LOG.warn("cart_product_display_unavailable traceId={} failureType={}",
                    safeTraceId, exception.getClass().getSimpleName());
            throw new ProductDisplayDependencyException(Failure.TOKEN_UNAVAILABLE);
        } catch (RetryableException exception) {
            LOG.warn("cart_product_display_unavailable traceId={} failureType=TIMEOUT", safeTraceId);
            throw new ProductDisplayDependencyException(
                    exception.getCause() == null ? Failure.TIMEOUT : Failure.CONNECTION);
        } catch (FeignException exception) {
            LOG.warn("cart_product_display_unavailable traceId={} failureType=HTTP_{}",
                    safeTraceId, exception.status());
            throw new ProductDisplayDependencyException(mapHttpFailure(exception.status()));
        }
    }

    private ProductDisplayBatch mapAndValidate(
            List<UUID> requestedIds, ProductDisplayWireModels.Envelope envelope) {
        if (envelope == null || !envelope.success() || envelope.data() == null
                || envelope.data().variants() == null) {
            throw new ProductDisplayDependencyException(Failure.MALFORMED_RESPONSE);
        }
        Map<UUID, ProductDisplayWireModels.Variant> byId = new LinkedHashMap<>();
        for (ProductDisplayWireModels.Variant variant : envelope.data().variants()) {
            if (variant == null || variant.variantId() == null || byId.put(variant.variantId(), variant) != null) {
                throw new ProductDisplayDependencyException(Failure.MALFORMED_RESPONSE);
            }
        }
        if (!byId.keySet().equals(new LinkedHashSet<>(requestedIds))) {
            throw new ProductDisplayDependencyException(Failure.MALFORMED_RESPONSE);
        }
        List<ProductDisplay> displays = requestedIds.stream()
                .map(id -> toDisplay(id, byId.get(id)))
                .toList();
        return ProductDisplayBatch.available(displays);
    }

    private static List<UUID> normalizeVariantIds(List<UUID> variantIds) {
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID variantId : variantIds) {
            if (variantId == null) {
                throw new ProductDisplayDependencyException(Failure.MALFORMED_RESPONSE);
            }
            unique.add(variantId);
        }
        return List.copyOf(unique);
    }

    private static Failure mapHttpFailure(int status) {
        if (status == 401) return Failure.UNAUTHORIZED;
        if (status == 403) return Failure.FORBIDDEN;
        if (status >= 500) return Failure.SERVER_ERROR;
        return status < 0 ? Failure.CONNECTION : Failure.MALFORMED_RESPONSE;
    }

    private ProductDisplay toDisplay(UUID requestedId, ProductDisplayWireModels.Variant variant) {
        if (variant == null) {
            throw new ProductDisplayDependencyException(Failure.MALFORMED_RESPONSE);
        }
        if (!requestedId.equals(variant.variantId())) {
            throw new ProductDisplayDependencyException(Failure.MALFORMED_RESPONSE);
        }
        if (variant.found() && variant.productId() == null) {
            throw new ProductDisplayDependencyException(Failure.MALFORMED_RESPONSE);
        }
        if (!variant.found() && (variant.productId() != null || variant.productName() != null
                || variant.productSlug() != null || variant.variantName() != null
                || variant.sku() != null || variant.basePrice() != null
                || variant.currency() != null || variant.primaryImageUrl() != null)) {
            throw new ProductDisplayDependencyException(Failure.MALFORMED_RESPONSE);
        }
        return new ProductDisplay(
                variant.variantId(), variant.found(), variant.sellable(), variant.productId(),
                variant.productSlug(), variant.productName(), variant.variantName(), variant.sku(),
                variant.basePrice(), variant.currency(), variant.primaryImageUrl());
    }

    private static Failure mapFailure(ProductDisplayRemoteException.Failure failure) {
        return switch (failure) {
            case UNAUTHORIZED -> Failure.UNAUTHORIZED;
            case FORBIDDEN -> Failure.FORBIDDEN;
            case SERVER_ERROR -> Failure.SERVER_ERROR;
            case MALFORMED_RESPONSE -> Failure.MALFORMED_RESPONSE;
        };
    }

    private static String normalizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) return UUID.randomUUID().toString();
        String normalized = traceId.trim();
        return normalized.length() > MAX_TRACE_LENGTH
                ? normalized.substring(0, MAX_TRACE_LENGTH)
                : normalized;
    }
}
