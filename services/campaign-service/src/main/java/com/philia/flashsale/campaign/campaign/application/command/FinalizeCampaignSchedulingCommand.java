package com.philia.flashsale.campaign.campaign.application.command;

import com.philia.flashsale.campaign.campaign.application.result.CampaignInventoryAllocation;
import com.philia.flashsale.campaign.campaign.application.result.ValidatedCampaignVariant;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Data passed to the short local transaction that freezes a scheduled Campaign.
 */
// Đây là bộ dữ liệu "chốt sổ" đóng gói
// toàn bộ kết quả xác minh từ các service khác (Product, Inventory) để chuẩn bị
// thực hiện một transaction ngắn tại campaign-service, nhằm chính thức đưa một
// chiến dịch Flash Sale từ trạng thái Draft/Pending sang trạng thái Scheduled
// (Đã chốt / Đóng băng).
public record FinalizeCampaignSchedulingCommand(
        UUID campaignId,
        UUID operationId,
        long expectedVersion,
        ValidatedCampaignVariant product,
        CampaignInventoryAllocation allocation,
        String actor,
        String traceId,
        Instant now) {

    public FinalizeCampaignSchedulingCommand {
        Objects.requireNonNull(campaignId, "Campaign id is required");
        Objects.requireNonNull(operationId, "Schedule operation id is required");
        Objects.requireNonNull(product, "Product validation result is required");
        Objects.requireNonNull(allocation, "Inventory allocation is required");
        Objects.requireNonNull(now, "Schedule time is required");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Campaign version must not be negative");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Campaign actor is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Trace id is required");
        }
    }
}
