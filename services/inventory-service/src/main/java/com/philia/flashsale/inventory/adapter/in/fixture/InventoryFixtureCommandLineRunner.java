package com.philia.flashsale.inventory.adapter.in.fixture;

import com.philia.flashsale.inventory.configuration.InventoryFixtureProperties;
import com.philia.flashsale.inventory.stock.application.command.InitializeInventoryCommand;
import com.philia.flashsale.inventory.stock.application.port.in.InitializeInventoryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * One-shot driving adapter for an explicitly enabled Inventory fixture Job.
 *
 * <p>The adapter delegates to the application use case and deliberately has no JDBC, JPA, HTTP,
 * Kafka, or Redis access. Normal Inventory Deployments do not create this bean.</p>
 */
@Component
@ConditionalOnProperty(prefix = "flashsale.inventory.fixture", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(InventoryFixtureProperties.class)
public class InventoryFixtureCommandLineRunner implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryFixtureCommandLineRunner.class);

    private final InitializeInventoryUseCase initializeInventory;
    private final InventoryFixtureProperties properties;
    private final ConfigurableApplicationContext context;

    public InventoryFixtureCommandLineRunner(
            InitializeInventoryUseCase initializeInventory,
            InventoryFixtureProperties properties,
            ConfigurableApplicationContext context) {
        this.initializeInventory = initializeInventory;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(String... args) {
        try {
            properties.validate();
            var result = initializeInventory.initialize(new InitializeInventoryCommand(
                    properties.getVariantId(),
                    properties.getSkuSnapshot().trim(),
                    properties.getQuantity(),
                    properties.getReason().trim()));
            LOGGER.info("Inventory fixture initialized: variantId={}, quantity={}, status=READY",
                    result.variantId(), result.onHandQuantity());
        } finally {
            context.close();
        }
    }
}
