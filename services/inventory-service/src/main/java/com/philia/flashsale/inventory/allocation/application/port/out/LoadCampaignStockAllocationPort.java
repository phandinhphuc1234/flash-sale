package com.philia.flashsale.inventory.allocation.application.port.out;

import com.philia.flashsale.inventory.allocation.domain.model.CampaignStockAllocation;
import java.util.Optional;
import java.util.UUID;

public interface LoadCampaignStockAllocationPort {
    Optional<CampaignStockAllocation> findByRequestId(UUID requestId);
}
