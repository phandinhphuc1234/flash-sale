package com.philia.flashsale.flashsale.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FlashSaleConfigurationPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class);

    @Test
    void bindsTheApprovedMvpDefaults() {
        contextRunner
                .withPropertyValues(
                        "flashsale.reservation.ttl=5m",
                        "flashsale.reservation.idempotency-retention-after-campaign-end=24h",
                        "flashsale.reservation.idempotency-key-max-length=128",
                        "flashsale.redis.handoff-stream=fs:{hot}:handoff",
                        "flashsale.redis.consumer-group=flashsale-durable-acceptance-v1",
                        "flashsale.redis.poll-timeout=1s",
                        "flashsale.redis.batch-size=100",
                        "flashsale.redis.reclaim-idle=30s",
                        "flashsale.campaign-projection.topic=campaign.lifecycle.v1",
                        "flashsale.campaign-projection.consumer-group=flashsale-campaign-projection-v1",
                        "flashsale.campaign-projection.recovery-interval=5s",
                        "flashsale.campaign-projection.max-deliveries=3",
                        "flashsale.campaign-projection.retry-delays=250ms,500ms",
                        "flashsale.campaign-projection.snapshot-base-url=http://localhost:8086",
                        "flashsale.outbox.topic=flashsale.purchase.events.v1",
                        "flashsale.outbox.poll-interval=500ms",
                        "flashsale.outbox.batch-size=100",
                        "flashsale.outbox.claim-lease=30s",
                        "flashsale.outbox.retry-backoff-cap=60s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FlashSaleProperties.class).ttl())
                            .hasSeconds(300);
                    assertThat(context.getBean(RedisHotPathProperties.class).batchSize())
                            .isEqualTo(100);
                    assertThat(context.getBean(CampaignProjectionProperties.class).retryDelays())
                            .hasSize(2);
                    assertThat(context.getBean(OutboxProperties.class).retryBackoffCap())
                            .hasSeconds(60);
                });
    }

    @Test
    void rejectsAnIdempotencyKeyLimitOutsideTheApprovedTransportRange() {
        contextRunner
                .withPropertyValues(
                        "flashsale.reservation.ttl=5m",
                        "flashsale.reservation.idempotency-retention-after-campaign-end=24h",
                        "flashsale.reservation.idempotency-key-max-length=129")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            FlashSaleProperties.class,
            RedisHotPathProperties.class,
            CampaignProjectionProperties.class,
            OutboxProperties.class
    })
    static class BindingConfiguration {
    }
}
