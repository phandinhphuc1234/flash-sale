package com.philia.flashsale.flashsale.campaignprojection.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Loads campaign identifiers whose control-plane recovery attempt is due. */
public interface LoadDueCampaignRecoveryPort {
    List<UUID> loadDue(Instant now);
}
