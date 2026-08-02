package com.philia.flashsale.campaign.campaign.adapter.in.web.admin.mapper;

import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.CreateCampaignRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ReplaceCampaignItemRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ReplaceCampaignMetadataRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.response.CampaignItemResponse;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.response.CampaignResponse;
import com.philia.flashsale.campaign.campaign.application.command.CreateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.ReplaceCampaignItemCommand;
import com.philia.flashsale.campaign.campaign.application.command.ReplaceCampaignMetadataCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;
import com.philia.flashsale.campaign.campaign.application.result.CampaignItemResult;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Maps only the Campaign admin HTTP boundary to application commands/results. */
@org.springframework.stereotype.Component
public final class CampaignAdminWebMapper {

    public CreateCampaignCommand toCommand(CreateCampaignRequest request) {
        return new CreateCampaignCommand(
                request.code(), request.name(), request.startAt(), request.endAt());
    }

    public ReplaceCampaignMetadataCommand toCommand(
            UUID campaignId, long expectedVersion, ReplaceCampaignMetadataRequest request) {
        return new ReplaceCampaignMetadataCommand(
                campaignId, expectedVersion, request.name(), request.startAt(), request.endAt());
    }

    public ReplaceCampaignItemCommand toCommand(
            UUID campaignId, long expectedVersion, ReplaceCampaignItemRequest request) {
        return new ReplaceCampaignItemCommand(
                campaignId,
                expectedVersion,
                request.variantId(),
                toCampaignMoney(request.campaignPrice()),
                request.requestedQuantity(),
                request.purchaseLimitPerUser());
    }

    public CampaignResponse toResponse(CampaignDetailResult result) {
        return new CampaignResponse(
                result.id(),
                result.code(),
                result.name(),
                toStatus(result.status()),
                result.startAt(),
                result.endAt(),
                result.scheduledAt(),
                result.activatedAt(),
                result.endedAt(),
                result.version(),
                toResponse(result.item()),
                result.createdAt(),
                result.updatedAt());
    }

    public CampaignItemResponse toResponse(CampaignItemResult result) {
        if (result == null) {
            return null;
        }
        return new CampaignItemResponse(
                result.productId(),
                result.variantId(),
                result.inventoryAllocationId(),
                result.variantSku(),
                result.basePrice(),
                result.currency(),
                result.campaignPrice(),
                result.requestedQuantity(),
                result.allocatedQuantity(),
                result.purchaseLimitPerUser());
    }

    public CampaignMoney toCampaignMoney(BigDecimal amount) {
        return amount == null ? null : CampaignMoney.vnd(amount);
    }

    public String toStatus(CampaignStatus status) {
        return status == null ? null : status.name();
    }
}
