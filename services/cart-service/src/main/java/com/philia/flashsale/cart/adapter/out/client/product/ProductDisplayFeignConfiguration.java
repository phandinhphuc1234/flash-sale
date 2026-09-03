package com.philia.flashsale.cart.adapter.out.client.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import org.springframework.context.annotation.Bean;

/** Product-client-only policies: no automatic retry and no remote body leakage. */
class ProductDisplayFeignConfiguration {

    @Bean
    ErrorDecoder productDisplayErrorDecoder(ObjectMapper objectMapper) {
        return (methodKey, response) -> new ProductDisplayRemoteException(
                classify(response.status(), readErrorCode(response, objectMapper)));
    }

    @Bean
    Retryer productDisplayRetryer() {
        return Retryer.NEVER_RETRY;
    }

    private static ProductDisplayRemoteException.Failure classify(int status, String code) {
        if ("PRODUCT_INTERNAL_ERROR".equals(code)) {
            return ProductDisplayRemoteException.Failure.SERVER_ERROR;
        }
        if (status == 401) return ProductDisplayRemoteException.Failure.UNAUTHORIZED;
        if (status == 403) return ProductDisplayRemoteException.Failure.FORBIDDEN;
        if (status >= 500) return ProductDisplayRemoteException.Failure.SERVER_ERROR;
        return ProductDisplayRemoteException.Failure.MALFORMED_RESPONSE;
    }

    private static String readErrorCode(Response response, ObjectMapper objectMapper) {
        if (response.body() == null) return null;
        try (var input = response.body().asInputStream()) {
            JsonNode body = objectMapper.readTree(input);
            return body.path("errorCode").isTextual() ? body.path("errorCode").textValue() : null;
        } catch (IOException ignored) {
            return null;
        }
    }
}
