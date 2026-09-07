package com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.entity.RegularPurchaseRequestJpaEntity;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseLine;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import java.math.BigDecimal;
import java.util.ArrayList;

/** Explicit JSON/JPA mapper; no persistence representation escapes the regular-purchase adapter. */
public class RegularPurchasePersistenceMapper {

    private final ObjectMapper objectMapper;

    public RegularPurchasePersistenceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RegularPurchaseRequest toDomain(RegularPurchaseRequestJpaEntity entity) {
        try {
            JsonNode snapshot = objectMapper.readTree(entity.getSnapshotPayload());
            Long submittedCartVersion = snapshot.hasNonNull("submittedCartVersion")
                    ? snapshot.get("submittedCartVersion").longValue() : null;
            ArrayList<RegularPurchaseLine> lines = new ArrayList<>();
            for (JsonNode line : snapshot.withArray("lines")) {
                Long cartItemVersion = line.hasNonNull("cartItemVersion")
                        ? line.get("cartItemVersion").longValue() : null;
                lines.add(new RegularPurchaseLine(
                        java.util.UUID.fromString(line.required("variantId").textValue()),
                        line.required("quantity").longValue(),
                        Money.of(new BigDecimal(line.required("expectedUnitPrice").textValue())),
                        line.required("currency").textValue(), cartItemVersion));
            }
            return RegularPurchaseRequest.restore(entity.getId(), entity.getShopperId(), entity.getIdempotencyKey(),
                    entity.getRequestFingerprint(), entity.getSource(), entity.getProposedOrderId(),
                    entity.getProposedHoldId(), lines, submittedCartVersion, entity.getCartId(), entity.getCartVersion(),
                    entity.getState(), entity.getHoldExpiresAt(), entity.getOrderId(), entity.getRejectionCode(),
                    entity.getCreatedAt(), entity.getUpdatedAt());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("regular purchase intake snapshot is invalid", exception);
        }
    }

    public void apply(RegularPurchaseRequestJpaEntity entity, RegularPurchaseRequest request,
            String traceparent, String tracestate) {
        entity.apply(request.id(), request.shopperId(), request.idempotencyKey(), request.requestFingerprint(),
                request.source(), request.state(), request.proposedOrderId(), request.proposedHoldId(), request.cartId(),
                request.cartVersion(), snapshot(request), request.holdExpiresAt(), request.orderId(),
                request.rejectionCode(), rejectionPayload(request), acceptedResponse(request), traceparent, tracestate,
                request.createdAt(), request.updatedAt());
    }

    private String snapshot(RegularPurchaseRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        if (request.submittedCartVersion() == null) {
            root.putNull("submittedCartVersion");
        } else {
            root.put("submittedCartVersion", request.submittedCartVersion());
        }
        ArrayNode lines = root.putArray("lines");
        for (RegularPurchaseLine line : request.lines()) {
            ObjectNode json = lines.addObject();
            json.put("variantId", line.variantId().toString());
            json.put("quantity", line.quantity());
            json.put("expectedUnitPrice", line.expectedUnitPrice().amount().toPlainString());
            json.put("currency", line.currency());
            if (line.cartItemVersion() == null) {
                json.putNull("cartItemVersion");
            } else {
                json.put("cartItemVersion", line.cartItemVersion());
            }
        }
        return write(root);
    }

    private String rejectionPayload(RegularPurchaseRequest request) {
        if (request.rejectionCode() == null) return null;
        return write(objectMapper.createObjectNode().put("code", request.rejectionCode()));
    }

    private String acceptedResponse(RegularPurchaseRequest request) {
        if (request.orderId() == null) return null;
        return write(objectMapper.createObjectNode()
                .put("purchaseRequestId", request.id().toString())
                .put("orderId", request.orderId().toString())
                .put("source", request.source().name())
                .put("stockHoldExpiresAt", request.holdExpiresAt().toString()));
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("regular purchase intake snapshot cannot be serialized", exception);
        }
    }
}
