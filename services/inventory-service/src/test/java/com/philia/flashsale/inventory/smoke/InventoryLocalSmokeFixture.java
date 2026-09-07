package com.philia.flashsale.inventory.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.inventory.stock.application.command.InitializeInventoryCommand;
import com.philia.flashsale.inventory.stock.application.port.in.InitializeInventoryUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Opt-in fixture for a live local smoke environment while Inventory initialization transport is
 * intentionally deferred. Normal Maven builds do not discover this class by naming convention.
 */
@SpringBootTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.task.scheduling.enabled=false"
})
@EnabledIfSystemProperty(named = "inventory.local.fixture.enabled", matches = "true")
class InventoryLocalSmokeFixture {

    private static final String ACTION_PROPERTY = "inventory.local.fixture.action";
    private static final String VARIANT_ID_PROPERTY = "inventory.local.fixture.variant-id";
    private static final String SKU_PROPERTY = "inventory.local.fixture.sku";
    private static final String QUANTITY_PROPERTY = "inventory.local.fixture.quantity";

    @Autowired
    private InitializeInventoryUseCase initializeInventory;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void prepareOrRemoveDisposableFixture() {
        UUID variantId = UUID.fromString(requiredProperty(VARIANT_ID_PROPERTY));
        String action = System.getProperty(ACTION_PROPERTY, "prepare").trim();

        if ("cleanup".equalsIgnoreCase(action)) {
            removeFixture(variantId);
            assertThat(inventoryItemIds(variantId)).isEmpty();
            return;
        }
        if (!"prepare".equalsIgnoreCase(action)) {
            throw new IllegalArgumentException("Fixture action must be prepare or cleanup");
        }

        removeFixture(variantId);
        long quantity = Long.parseLong(requiredProperty(QUANTITY_PROPERTY));
        var result = initializeInventory.initialize(new InitializeInventoryCommand(
                variantId,
                requiredProperty(SKU_PROPERTY),
                quantity,
                "FEATURE_017_LOCAL_SMOKE"));

        assertThat(result.variantId()).isEqualTo(variantId);
        assertThat(result.onHandQuantity()).isEqualTo(quantity);
        assertThat(result.campaignAllocatedQuantity()).isZero();
        assertThat(result.availableQuantity()).isEqualTo(quantity);
    }

    private void removeFixture(UUID variantId) {
        List<UUID> itemIds = inventoryItemIds(variantId);
        for (UUID itemId : itemIds) {
            List<UUID> allocationIds = jdbc.query(
                    "SELECT id FROM campaign_stock_allocations WHERE inventory_item_id = ?",
                    (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                    itemId);
            for (UUID allocationId : allocationIds) {
                jdbc.update("DELETE FROM outbox_events WHERE aggregate_id = ?", allocationId);
            }
            jdbc.update("DELETE FROM stock_movements WHERE inventory_item_id = ?", itemId);
            jdbc.update("DELETE FROM campaign_stock_allocations WHERE inventory_item_id = ?", itemId);
            // Historical regular holds deliberately use RESTRICT FKs. The disposable fixture
            // may be reused after a prior smoke run, so remove only the hold-item links for this
            // variant before deleting its inventory row; leave the other hold history intact.
            jdbc.update("DELETE FROM regular_stock_hold_items WHERE inventory_item_id = ?", itemId);
            jdbc.update("DELETE FROM inventory_items WHERE id = ?", itemId);
        }
    }

    private List<UUID> inventoryItemIds(UUID variantId) {
        return jdbc.query(
                "SELECT id FROM inventory_items WHERE variant_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                variantId);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
