package com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis;

import com.philia.flashsale.flashsale.campaignprojection.application.port.out.LoadDueCampaignRecoveryPort;
import com.philia.flashsale.flashsale.campaignprojection.application.port.out.QueueCampaignRecoveryPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Stores and scans the Redis sorted-set queue used by the control-plane recovery job. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class CampaignProjectionRecoveryRedisAdapter
        implements QueueCampaignRecoveryPort, LoadDueCampaignRecoveryPort {

    private final StringRedisTemplate redis;

    public CampaignProjectionRecoveryRedisAdapter(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public void queue(UUID campaignId, Instant nextAttemptAt) {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        redis.opsForZSet().add(CampaignProjectionRedisKeys.recoveryQueue(),
                campaignId.toString(), nextAttemptAt.toEpochMilli());
    }

    @Override
    public List<UUID> loadDue(Instant now) {
        Objects.requireNonNull(now, "now");
        Set<String> values = redis.opsForZSet().rangeByScore(
                CampaignProjectionRedisKeys.recoveryQueue(), 0, now.toEpochMilli());
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(UUID::fromString).toList();
    }
}
