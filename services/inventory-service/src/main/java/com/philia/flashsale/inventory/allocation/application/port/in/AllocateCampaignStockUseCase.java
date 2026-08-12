package com.philia.flashsale.inventory.allocation.application.port.in;

import com.philia.flashsale.inventory.allocation.application.command.AllocateCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.result.CampaignStockAllocationResult;

public interface AllocateCampaignStockUseCase {
    CampaignStockAllocationResult allocate(AllocateCampaignStockCommand command);
}
