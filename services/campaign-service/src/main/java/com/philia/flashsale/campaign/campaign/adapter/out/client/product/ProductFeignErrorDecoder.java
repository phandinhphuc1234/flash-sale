package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;

/** Maps Product status plus stable error code without exposing remote response text. */
final class ProductFeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    ProductFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String errorCode = readErrorCode(response);
        ProductRemoteException.Failure failure;
        if (status == 400) {
            failure = ProductRemoteException.Failure.VALIDATION;
        } else if (status == 404 && "PRODUCT_VARIANT_NOT_FOUND".equals(errorCode)) {
            failure = ProductRemoteException.Failure.VARIANT_NOT_FOUND;
        } else if (status == 409 && "PRODUCT_VARIANT_NOT_SELLABLE".equals(errorCode)) {
            failure = ProductRemoteException.Failure.VARIANT_NOT_SELLABLE;
        } else if (status == 401 || status == 403) {
            failure = ProductRemoteException.Failure.TOKEN_REJECTED;
        } else {
            failure = ProductRemoteException.Failure.UNAVAILABLE;
        }
        return new ProductRemoteException(failure, status);
    }

    private String readErrorCode(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (var input = response.body().asInputStream()) {
            JsonNode body = objectMapper.readTree(input);
            return body.path("errorCode").isTextual() ? body.path("errorCode").textValue() : null;
        } catch (IOException ignored) {
            return null;
        }
    }
}
