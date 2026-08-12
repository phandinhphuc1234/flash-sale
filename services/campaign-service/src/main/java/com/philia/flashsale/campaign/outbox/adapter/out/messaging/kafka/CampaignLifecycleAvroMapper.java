package com.philia.flashsale.campaign.outbox.adapter.out.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.campaign.campaign.domain.event.CampaignActivated;
import com.philia.flashsale.campaign.campaign.domain.event.CampaignScheduled;
import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledDataV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledItemV1;
import com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1;
import java.util.Objects;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;

/** Maps the canonical outbox JSON to generated Avro records at the Kafka boundary. */
@Component
final class CampaignLifecycleAvroMapper {

    private final ObjectMapper objectMapper;

    CampaignLifecycleAvroMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    SpecificRecord toRecord(OutboxClaim claim) {
        Objects.requireNonNull(claim, "Outbox claim is required");
        return switch (claim.eventType()) {
            case "CampaignScheduled" -> toScheduled(claim);
            case "CampaignActivated" -> toActivated(claim);
            default -> throw new IllegalArgumentException(
                    "Unsupported Campaign lifecycle event type: " + claim.eventType());
        };
    }

    private CampaignScheduledV1 toScheduled(OutboxClaim claim) {
        CampaignScheduled event = read(claim.payload(), CampaignScheduled.class);
        CampaignScheduled.Data data = event.data();
        CampaignScheduledItemV1 item = new CampaignScheduledItemV1(
                data.variantId(),
                data.inventoryAllocationId(),
                data.variantSku(),
                data.campaignPrice(),
                data.currency(),
                data.allocatedQuantity(),
                data.purchaseLimitPerUser());
        CampaignScheduledV1 record = new CampaignScheduledV1(
                event.eventId(), event.eventType(), event.eventVersion(), event.aggregateType(),
                event.aggregateId(), event.aggregateVersion(), event.occurredAt(),
                new CampaignScheduledDataV1(
                        data.campaignId(), data.campaignCode(), data.startAt(), data.endAt(), item));
        assertStableEventId(claim, record);
        return record;
    }

    private CampaignActivatedV1 toActivated(OutboxClaim claim) {
        CampaignActivated event = read(claim.payload(), CampaignActivated.class);
        CampaignActivated.Data data = event.data();
        CampaignActivatedV1 record = new CampaignActivatedV1(
                event.eventId(), event.eventType(), event.eventVersion(), event.aggregateType(),
                event.aggregateId(), event.aggregateVersion(), event.occurredAt(),
                new CampaignActivatedDataV1(data.campaignId(), data.startAt(), data.endAt()));
        assertStableEventId(claim, record);
        return record;
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Campaign outbox payload is not valid JSON", exception);
        }
    }

    private void assertStableEventId(OutboxClaim claim, SpecificRecord record) {
        int eventIdPosition = record.getSchema().getField("eventId").pos();
        Object eventId = record.get(eventIdPosition);
        if (!claim.id().equals(eventId)) {
            throw new IllegalArgumentException("Campaign outbox identity does not match Avro eventId");
        }
    }
}
