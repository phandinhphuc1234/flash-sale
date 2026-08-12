package com.philia.flashsale.inventory.allocation.application.port.in;

import com.philia.flashsale.inventory.allocation.application.command.ReleaseCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.result.CampaignStockAllocationResult;

public interface ReleaseCampaignStockUseCase {
    CampaignStockAllocationResult release(ReleaseCampaignStockCommand command);
}
