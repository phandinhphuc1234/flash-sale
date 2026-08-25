package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Kafka command topic and bounded retry settings for Flash Sale reservation commands. */
@Validated
@ConfigurationProperties(prefix = "flashsale.reservation-commands")
public record ReservationCommandProperties(
        @NotBlank String topic,
        @NotBlank String consumerGroup,
        @NotBlank String dltTopic,
        @NotNull List<Duration> retryDelays) {

    public ReservationCommandProperties {
        // Keep the command boundary bootable for disabled-runtime tests and for a
        // minimal local profile; deployment overlays may still override each value.
        topic = topic == null ? "flashsale.purchase.commands.v1" : topic;
        consumerGroup = consumerGroup == null ? "flashsale-reservation-commands-v1" : consumerGroup;
        dltTopic = dltTopic == null ? "flashsale.flash-sale.purchase-command.dlt.v1" : dltTopic;
        retryDelays = retryDelays == null
                ? List.of(Duration.ofMillis(500), Duration.ofSeconds(2))
                : List.copyOf(retryDelays);
    }
}
