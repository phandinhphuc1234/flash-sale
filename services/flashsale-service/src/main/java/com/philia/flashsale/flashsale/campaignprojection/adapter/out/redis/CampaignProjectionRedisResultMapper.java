package com.philia.flashsale.flashsale.campaignprojection.adapter.out.redis;

import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;
import java.util.List;

/** Decodes the small, typed result tuple returned by projection Lua scripts. */
public final class CampaignProjectionRedisResultMapper {
    public CampaignProjectionUpdateResult map(List<?> result) {
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("Campaign projection script returned no result");
        }
        return switch (String.valueOf(result.get(0))) {
            case "APPLIED" -> CampaignProjectionUpdateResult.APPLIED;
            case "NOOP_STALE" -> CampaignProjectionUpdateResult.NOOP_STALE;
            case "RECOVERY_REQUIRED" -> CampaignProjectionUpdateResult.RECOVERY_REQUIRED;
            default -> throw new IllegalStateException("Unknown Campaign projection result");
        };
    }
}
