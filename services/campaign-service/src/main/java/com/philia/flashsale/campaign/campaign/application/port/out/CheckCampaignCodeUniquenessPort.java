package com.philia.flashsale.campaign.campaign.application.port.out;

/** Outbound boundary for Campaign-owned case-insensitive code uniqueness. */
public interface CheckCampaignCodeUniquenessPort {

    boolean existsByCode(String normalizedCode);
}
