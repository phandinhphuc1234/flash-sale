package com.philia.flashsale.inventory.configuration;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires Inventory-owned regular-hold policy without leaking framework types into the domain. */
@Configuration
@EnableConfigurationProperties(InventoryRegularHoldProperties.class)
public class InventoryRegularHoldConfiguration {
    @Bean
    Clock inventoryClock(InventoryRegularHoldProperties properties) {
        properties.validate();
        return Clock.systemUTC();
    }
}
