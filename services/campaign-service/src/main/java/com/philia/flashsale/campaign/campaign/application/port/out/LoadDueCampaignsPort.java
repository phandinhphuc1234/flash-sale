package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.time.Instant;
import java.util.List;

/** Reads due lifecycle candidates from the Campaign-owned database. */
public interface LoadDueCampaignsPort {

    List<Campaign> findDueForActivation(Instant now, int limit);

    List<Campaign> findDueForEnding(Instant now, int limit);
}
