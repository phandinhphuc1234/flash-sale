package com.philia.flashsale.flashsale.campaignprojection.adapter.in.messaging.kafka;

import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import com.philia.flashsale.flashsale.campaignprojection.application.command.ApplyCampaignActivatedCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.command.ApplyCampaignScheduledCommand;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps only approved generated Campaign SpecificRecords into application
 * commands.
 */
@Component
public final class CampaignLifecycleAvroMapper {

    public ApplyCampaignScheduledCommand toScheduled(String key, CampaignScheduledV1 record) {
        validateEnvelope(key, record.getEventType(), record.getEventVersion(), record.getAggregateType(),
                record.getAggregateId(), record.getAggregateVersion(), "CampaignScheduled");
        var data = require(record.getData(), "CampaignScheduled data is required");
        requireSameId(record.getAggregateId(), data.getCampaignId(), "aggregate/data campaign ID mismatch");
        var item = require(data.getItem(), "CampaignScheduled item is required");
        CampaignItemProjection projection = new CampaignItemProjection(
                require(item.getVariantId(), "variantId is required"),
                require(item.getInventoryAllocationId(), "inventoryAllocationId is required"),
                requireText(item.getVariantSku(), "variantSku is required"),
                require(item.getCampaignPrice(), "campaignPrice is required"),
                requireText(item.getCurrency(), "currency is required"),
                item.getAllocatedQuantity(), item.getPurchaseLimitPerUser());
        return new ApplyCampaignScheduledCommand(
                data.getCampaignId(), record.getAggregateVersion(),
                data.getStartAt(), data.getEndAt(), projection, record.getOccurredAt());
    }

    // Translate the CampaignActivatedV1 Avro record into an application command,
    // validating the envelope and data.
    public ApplyCampaignActivatedCommand toActivated(String key, CampaignActivatedV1 record) {
        validateEnvelope(key, record.getEventType(), record.getEventVersion(), record.getAggregateType(),
                record.getAggregateId(), record.getAggregateVersion(), "CampaignActivated");
        var data = require(record.getData(), "CampaignActivated data is required");
        requireSameId(record.getAggregateId(), data.getCampaignId(), "aggregate/data campaign ID mismatch");
        return new ApplyCampaignActivatedCommand(
                data.getCampaignId(), record.getAggregateVersion(),
                data.getStartAt(), data.getEndAt(), record.getOccurredAt());
    }

    // Validate the envelope of the Campaign lifecycle record, ensuring the event
    // type, version, aggregate type, and IDs match expectations.
    private void validateEnvelope(String key, String eventType, int eventVersion, String aggregateType,
            UUID aggregateId, long aggregateVersion, String expectedEventType) {
        if (!expectedEventType.equals(eventType)) {
            throw new CampaignLifecycleRecordException("Unexpected Campaign event type");
        }
        if (eventVersion != 1) {
            throw new CampaignLifecycleRecordException("Unsupported Campaign event version");
        }
        if (aggregateType == null || !"CAMPAIGN".equalsIgnoreCase(aggregateType)) {
            throw new CampaignLifecycleRecordException("Unexpected Campaign aggregate type");
        }
        if (aggregateId == null || key == null || !aggregateId.toString().equals(key)) {
            throw new CampaignLifecycleRecordException("Kafka key must equal aggregate ID");
        }
        if (aggregateVersion <= 0) {
            throw new CampaignLifecycleRecordException("Campaign aggregate version must be positive");
        }
    }

    private static void requireSameId(UUID expected, UUID actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new CampaignLifecycleRecordException(message);
        }
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new CampaignLifecycleRecordException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CampaignLifecycleRecordException(message);
        }
        return value;
    }
}
