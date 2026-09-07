package com.philia.flashsale.order.regularpurchase.adapter.out.client.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;

/** Maps Product status plus stable error code without retaining remote response text. */
final class ProductPurchaseQuoteFeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    ProductPurchaseQuoteFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String code = readErrorCode(response);
        ProductPurchaseQuoteRemoteException.Failure failure = status == 400
                && "PRODUCT_PURCHASE_QUOTE_VALIDATION_ERROR".equals(code)
                ? ProductPurchaseQuoteRemoteException.Failure.REJECTED
                : status == 401 || status == 403
                        ? ProductPurchaseQuoteRemoteException.Failure.TOKEN_REJECTED
                        : ProductPurchaseQuoteRemoteException.Failure.UNAVAILABLE;
        return new ProductPurchaseQuoteRemoteException(failure, status);
    }

    private String readErrorCode(Response response) {
        if (response.body() == null) return null;
        try (var input = response.body().asInputStream()) {
            JsonNode body = objectMapper.readTree(input);
            return body.path("errorCode").isTextual() ? body.path("errorCode").textValue() : null;
        } catch (IOException ignored) {
            return null;
        }
    }
}
