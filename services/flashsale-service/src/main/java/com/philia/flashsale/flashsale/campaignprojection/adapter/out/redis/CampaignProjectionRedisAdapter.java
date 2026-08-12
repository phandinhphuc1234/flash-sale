package com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis;

import com.philia.flashsale.flashsale.campaignprojection.application.port.out.StoreCampaignProjectionPort;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/** Redis driven adapter executing each projection mutation as one atomic Lua operation. */
public final class CampaignProjectionRedisAdapter implements StoreCampaignProjectionPort {
    private final StringRedisTemplate redis;
    private final RedisScript<List> scheduledScript;
    private final RedisScript<List> activatedScript;
    private final RedisScript<List> recoveredScript;
    private final CampaignProjectionRedisResultMapper resultMapper = new CampaignProjectionRedisResultMapper();

    public CampaignProjectionRedisAdapter(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.scheduledScript = script("redis/campaign/apply-campaign-scheduled.lua");
        this.activatedScript = script("redis/campaign/apply-campaign-activated.lua");
        this.recoveredScript = script("redis/campaign/apply-campaign-recovered.lua");
    }

    @Override
    public CampaignProjectionUpdateResult applyScheduled(CampaignSaleProjection projection) {
        CampaignItemProjection item = projection.item();
        return execute(scheduledScript,
                List.of(
                        CampaignProjectionRedisKeys.meta(projection.campaignId()),
                        CampaignProjectionRedisKeys.stock(projection.campaignId()),
                        CampaignProjectionRedisKeys.item(projection.campaignId(), item.variantId()),
                        CampaignProjectionRedisKeys.recoveryQueue()),
                projection.campaignId().toString(), Long.toString(projection.aggregateVersion()),
                Long.toString(projection.startsAt().toEpochMilli()), Long.toString(projection.endsAt().toEpochMilli()),
                Long.toString(projection.updatedAt().toEpochMilli()), item.variantId().toString(),
                item.inventoryAllocationId().toString(), item.skuSnapshot(), item.saleUnitPrice().toPlainString(),
                item.currency(), Long.toString(item.allocatedQuantity()), Long.toString(item.perUserLimit()));
    }

    @Override
    public CampaignProjectionUpdateResult applyActivated(java.util.UUID campaignId, long aggregateVersion,
            java.time.Instant startsAt, java.time.Instant endsAt, java.time.Instant updatedAt) {
        return execute(activatedScript,
                List.of(CampaignProjectionRedisKeys.meta(campaignId), CampaignProjectionRedisKeys.recoveryQueue()),
                campaignId.toString(), Long.toString(aggregateVersion), Long.toString(startsAt.toEpochMilli()),
                Long.toString(endsAt.toEpochMilli()), Long.toString(updatedAt.toEpochMilli()));
    }

    @Override
    public CampaignProjectionUpdateResult applyRecovered(CampaignSaleProjection projection) {
        CampaignItemProjection item = projection.item();
        return execute(recoveredScript,
                List.of(
                        CampaignProjectionRedisKeys.meta(projection.campaignId()),
                        CampaignProjectionRedisKeys.stock(projection.campaignId()),
                        CampaignProjectionRedisKeys.item(projection.campaignId(), item.variantId()),
                        CampaignProjectionRedisKeys.recoveryQueue()),
                projection.campaignId().toString(), Long.toString(projection.aggregateVersion()),
                projection.state().name(), Long.toString(projection.startsAt().toEpochMilli()),
                Long.toString(projection.endsAt().toEpochMilli()), Long.toString(projection.updatedAt().toEpochMilli()),
                item.variantId().toString(), item.inventoryAllocationId().toString(), item.skuSnapshot(),
                item.saleUnitPrice().toPlainString(), item.currency(), Long.toString(item.allocatedQuantity()),
                Long.toString(item.perUserLimit()));
    }

    private CampaignProjectionUpdateResult execute(RedisScript<List> script, List<String> keys, String... args) {
        List<?> result = redis.execute(script, keys, (Object[]) args);
        return resultMapper.map(result);
    }

    private RedisScript<List> script(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(List.class);
        return script;
    }
}
