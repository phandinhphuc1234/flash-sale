package com.philia.flashsale.inventory.allocation.application.command;

import java.util.UUID;

public record ReleaseCampaignStockCommand(UUID requestId, String reason) {
}
