package com.philia.flashsale.flashsale.campaignprojection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.philia.flashsale.flashsale.campaignprojection.domain.exception.InvalidCampaignProjectionException;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignProjectionState;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignSaleProjectionTests {
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant END = Instant.parse("2026-08-10T13:00:00Z");

    @Test
    void scheduledProjectionIsNotSellableUntilActivation() {
        CampaignSaleProjection projection = CampaignSaleProjection.scheduled(
                CAMPAIGN_ID, 1, START, END, item(), START);

        assertEquals(CampaignProjectionState.SCHEDULED, projection.state());
        assertFalse(projection.isSellableAt(START.plusSeconds(1)));
        assertTrue(projection.isNewerThan(0));
    }

    @Test
    void rejectsInvalidWindowPriceCurrencyAndLimits() {
        assertThrows(InvalidCampaignProjectionException.class,
                () -> CampaignSaleProjection.scheduled(CAMPAIGN_ID, 1, END, START, item(), START));
        assertThrows(InvalidCampaignProjectionException.class,
                () -> new CampaignItemProjection(UUID.randomUUID(), UUID.randomUUID(), "SKU",
                        new BigDecimal("1.00001"), "VND", 1, 1));
        assertThrows(InvalidCampaignProjectionException.class,
                () -> new CampaignItemProjection(UUID.randomUUID(), UUID.randomUUID(), "SKU",
                        BigDecimal.ONE, "vnd", 1, 1));
        assertThrows(InvalidCampaignProjectionException.class,
                () -> new CampaignItemProjection(UUID.randomUUID(), UUID.randomUUID(), "SKU",
                        BigDecimal.ONE, "VND", 0, 1));
    }

    private CampaignItemProjection item() {
        return new CampaignItemProjection(UUID.randomUUID(), UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 100, 2);
    }
}
