package com.philia.flashsale.campaign.campaign.application.exception;

import java.util.Objects;

/**
 * Campaign-owned failure taxonomy translated from Product and Inventory
 * transport outcomes.
 */
// Nhiệm vụ tối cao của class này là: Dịch (Translate) và đóng gói (Wrap) toàn
// bộ các lỗi từ hai dịch vụ phía dưới (Downstream Services) là
// Product Service và Inventory Service thành các mã lỗi nghiệp vụ chuẩn hóa
// thuộc sở hữu riêng của Campaign Service.
public final class CampaignDownstreamException extends RuntimeException {

    private final Failure failure;

    public CampaignDownstreamException(Failure failure) {
        super(Objects.requireNonNull(failure).message());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }

    public enum Failure {
        CAMPAIGN_VALIDATION_FAILED("Campaign validation request was rejected"),
        PRODUCT_VARIANT_NOT_FOUND("Product variant was not found"),
        PRODUCT_VARIANT_NOT_SELLABLE("Product variant is not sellable"),
        PRODUCT_SERVICE_UNAVAILABLE("Product service is temporarily unavailable"),
        INVENTORY_INSUFFICIENT_STOCK("Inventory stock is insufficient"),
        INVENTORY_ALLOCATION_REJECTED("Inventory allocation was rejected"),
        INVENTORY_ALLOCATION_CONFLICT("Inventory allocation request conflicts with a previous request"),
        INVENTORY_SERVICE_UNAVAILABLE("Inventory service is temporarily unavailable");

        private final String message;

        Failure(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}
