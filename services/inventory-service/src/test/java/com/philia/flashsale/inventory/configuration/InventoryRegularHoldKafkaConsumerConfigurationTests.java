package com.philia.flashsale.inventory.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

class InventoryRegularHoldKafkaConsumerConfigurationTests {

    @Test
    void routesARejectedCommandToTheDedicatedDltWithItsOriginalPartition() {
        InventoryRegularHoldProperties properties = new InventoryRegularHoldProperties();
        properties.setCommandDltTopic("flashsale.inventory.regular-hold.commands.dlt.v1");

        var destination = InventoryRegularHoldKafkaConsumerConfiguration.dltDestination(
                new ConsumerRecord<>("flashsale.inventory.regular-hold.commands.v1", 4, 19L, "key", "value"),
                properties);

        assertThat(destination.topic()).isEqualTo("flashsale.inventory.regular-hold.commands.dlt.v1");
        assertThat(destination.partition()).isEqualTo(4);
    }

    @Test
    void usesTheReviewedBoundedRetryScheduleBeforeDltRecovery() {
        BackOffExecution retries = new InventoryRegularHoldKafkaConsumerConfiguration.FixedDelays(
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10))).start();

        assertThat(retries.nextBackOff()).isEqualTo(1_000L);
        assertThat(retries.nextBackOff()).isEqualTo(3_000L);
        assertThat(retries.nextBackOff()).isEqualTo(10_000L);
        assertThat(retries.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }
}
