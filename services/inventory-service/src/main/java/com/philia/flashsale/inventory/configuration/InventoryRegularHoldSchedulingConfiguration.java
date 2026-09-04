package com.philia.flashsale.inventory.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables scheduler infrastructure only when an approved regular-hold worker is explicitly enabled. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.outbox-publisher-enabled", havingValue = "true")
public class InventoryRegularHoldSchedulingConfiguration {
}
