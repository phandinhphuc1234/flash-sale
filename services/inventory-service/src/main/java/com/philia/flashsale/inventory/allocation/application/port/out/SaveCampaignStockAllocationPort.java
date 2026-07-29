package com.philia.flashsale.inventory.allocation.application.port.out;

import com.philia.flashsale.inventory.allocation.domain.model.CampaignStockAllocation;

public interface SaveCampaignStockAllocationPort {
    CampaignStockAllocation save(CampaignStockAllocation allocation);
}
