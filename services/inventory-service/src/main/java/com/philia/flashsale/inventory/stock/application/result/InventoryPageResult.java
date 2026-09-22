package com.philia.flashsale.inventory.stock.application.result;

import java.util.List;

/** Application-owned page metadata; HTTP pagination is mapped at the web boundary. */
public record InventoryPageResult(
        List<InventoryListItemResult> content,
        int page,
        int size,
        long totalElements) {
    public InventoryPageResult {
        content = List.copyOf(content);
    }
}
