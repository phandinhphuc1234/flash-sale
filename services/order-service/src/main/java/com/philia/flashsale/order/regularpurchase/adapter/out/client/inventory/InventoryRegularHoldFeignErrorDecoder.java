package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;

/** Maps Inventory status and stable error codes, never raw downstream error text. */
final class InventoryRegularHoldFeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    InventoryRegularHoldFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        String code = readErrorCode(response);
        InventoryRegularHoldRemoteException.Failure failure;
        if (status == 409 && "INSUFFICIENT_STOCK".equals(code)) {
            failure = InventoryRegularHoldRemoteException.Failure.INSUFFICIENT_STOCK;
        } else if (status == 409 && "INVENTORY_ITEM_NOT_FOUND".equals(code)) {
            failure = InventoryRegularHoldRemoteException.Failure.ITEM_NOT_FOUND;
        } else if (status == 409 && "HOLD_IDENTITY_CONFLICT".equals(code)) {
            failure = InventoryRegularHoldRemoteException.Failure.IDENTITY_CONFLICT;
        } else if (status == 400 || status == 409) {
            failure = InventoryRegularHoldRemoteException.Failure.REJECTED;
        } else if (status == 401 || status == 403) {
            failure = InventoryRegularHoldRemoteException.Failure.TOKEN_REJECTED;
        } else {
            failure = InventoryRegularHoldRemoteException.Failure.UNAVAILABLE;
        }
        return new InventoryRegularHoldRemoteException(failure, status);
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
