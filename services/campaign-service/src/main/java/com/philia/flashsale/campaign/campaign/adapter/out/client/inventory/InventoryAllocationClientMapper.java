package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

import com.philia.flashsale.campaign.campaign.application.command.AllocateCampaignInventoryCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignInventoryAllocation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/** Maps between Campaign-owned application types and Inventory wire types. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InventoryAllocationClientMapper {

    @Mapping(target = "reason", constant = "CAMPAIGN_SCHEDULE")
    InventoryAllocationRequest toRequest(AllocateCampaignInventoryCommand command);

    @Mapping(target = "inventoryAllocationId", source = "id")
    CampaignInventoryAllocation toResult(InventoryAllocationResponse response);
}
