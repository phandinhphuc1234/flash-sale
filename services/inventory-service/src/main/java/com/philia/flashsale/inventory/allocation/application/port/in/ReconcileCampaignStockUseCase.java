package com.philia.flashsale.inventory.allocation.application.port.in;

import com.philia.flashsale.inventory.allocation.application.command.ReconcileCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.result.CampaignStockAllocationResult;

public interface ReconcileCampaignStockUseCase {
    CampaignStockAllocationResult reconcile(ReconcileCampaignStockCommand command);
}
