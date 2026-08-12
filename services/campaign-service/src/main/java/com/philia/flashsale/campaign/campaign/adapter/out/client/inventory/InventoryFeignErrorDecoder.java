package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;

/** Maps Inventory status plus stable error code without parsing client-facing message text. */
final class InventoryFeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    InventoryFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String errorCode = readErrorCode(response);
        InventoryRemoteException.Failure failure;
        if (status == 404 && "INVENTORY_NOT_FOUND".equals(errorCode)) {
            failure = InventoryRemoteException.Failure.NOT_FOUND;
        } else if (status == 409 && "INVENTORY_INSUFFICIENT_STOCK".equals(errorCode)) {
            failure = InventoryRemoteException.Failure.INSUFFICIENT_STOCK;
        } else if (status == 409 && "INVENTORY_ALLOCATION_REQUEST_CONFLICT".equals(errorCode)) {
            failure = InventoryRemoteException.Failure.REQUEST_CONFLICT;
        } else if (status == 400 || status == 409) {
            failure = InventoryRemoteException.Failure.REJECTED;
        } else if (status == 401 || status == 403) {
            failure = InventoryRemoteException.Failure.TOKEN_REJECTED;
        } else {
            failure = InventoryRemoteException.Failure.UNAVAILABLE;
        }
        return new InventoryRemoteException(failure, status);
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
