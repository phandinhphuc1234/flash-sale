package com.philia.flashsale.campaign.campaign.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignDraftDomainTests {

    private static final Instant START = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-08-01T11:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void createDraftNormalizesCodeAndTextFields() {
        Campaign campaign = Campaign.createDraft(
                UUID.randomUUID(),
                "  summer-01  ",
                "  Summer promotion  ",
                START,
                END,
                " admin ",
                CREATED_AT);

        assertEquals("SUMMER-01", campaign.code());
        assertEquals("Summer promotion", campaign.name());
        assertEquals("admin", campaign.createdBy());
        assertEquals(CampaignStatus.DRAFT, campaign.status());
        assertEquals(0, campaign.version());
    }

    @Test
    void createDraftRejectsInvalidTimeRange() {
        assertThrows(IllegalArgumentException.class, () -> Campaign.createDraft(
                UUID.randomUUID(), "SUMMER-01", "Summer promotion", START, START, "admin", CREATED_AT));
        assertThrows(IllegalArgumentException.class, () -> Campaign.createDraft(
                UUID.randomUUID(), "SUMMER-01", "Summer promotion", END, START, "admin", CREATED_AT));
    }

    @Test
    void replacingItemKeepsOnlyTheLatestItemAndAdvancesVersion() {
        Campaign campaign = draft();
        CampaignItem first = draftItem(100L, 10L, 2L);
        CampaignItem replacement = draftItem(200L, 20L, 3L);

        campaign.replaceItem(first, "admin", CREATED_AT.plusSeconds(1));
        campaign.replaceItem(replacement, "admin", CREATED_AT.plusSeconds(2));

        assertEquals(replacement, campaign.item());
        assertEquals(2, campaign.version());
    }

    @Test
    void metadataReplacementAdvancesVersionOnce() {
        Campaign campaign = draft();

        campaign.replaceMetadata(
                "Updated promotion",
                START.plusSeconds(300),
                END.plusSeconds(300),
                "editor",
                CREATED_AT.plusSeconds(1));

        assertEquals("Updated promotion", campaign.name());
        assertEquals(START.plusSeconds(300), campaign.startAt());
        assertEquals(END.plusSeconds(300), campaign.endAt());
        assertEquals("editor", campaign.updatedBy());
        assertEquals(1, campaign.version());
    }

    @Test
    void invalidItemPriceQuantityAndPurchaseLimitAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CampaignMoney.vnd(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> CampaignMoney.vnd(new BigDecimal("10.12345")));
        assertThrows(IllegalArgumentException.class, () -> new CampaignMoney(new BigDecimal("10"), "USD"));

        UUID variantId = UUID.randomUUID();
        CampaignMoney price = CampaignMoney.vnd(new BigDecimal("100"));
        assertThrows(IllegalArgumentException.class,
                () -> CampaignItem.createDraft(null, variantId, price, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> CampaignItem.createDraft(null, variantId, price, 10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CampaignItem.createDraft(null, variantId, price, 10, 11));
        assertThrows(IllegalArgumentException.class,
                () -> CampaignItem.createDraft(null, variantId, price, 10, 2)
                        .withProductSnapshot(UUID.randomUUID(), "SKU-1", price));
    }

    @Test
    void draftOnlyMutationIsRejectedAfterScheduling() {
        Campaign campaign = draft();
        CampaignItem completeItem = draftItem(100L, 10L, 2L)
                .withProductSnapshot(UUID.randomUUID(), "SKU-1", CampaignMoney.vnd(new BigDecimal("120")))
                .withInventoryAllocation(UUID.randomUUID(), 10L);

        campaign.replaceItem(completeItem, "admin", CREATED_AT.plusSeconds(1));
        campaign.markScheduled("scheduler", START.minusSeconds(1));

        assertEquals(CampaignStatus.SCHEDULED, campaign.status());
        assertEquals(2, campaign.version());
        assertThrows(IllegalStateException.class, () -> campaign.replaceMetadata(
                "Not editable", START, END, "admin", CREATED_AT.plusSeconds(3)));
        assertThrows(IllegalStateException.class, () -> campaign.replaceItem(
                draftItem(300L, 30L, 4L), "admin", CREATED_AT.plusSeconds(3)));
        assertEquals(2, campaign.version());
    }

    private static Campaign draft() {
        return Campaign.createDraft(
                UUID.randomUUID(), "SUMMER-01", "Summer promotion", START, END, "admin", CREATED_AT);
    }

    private static CampaignItem draftItem(long amount, long quantity, long purchaseLimit) {
        return CampaignItem.createDraft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CampaignMoney.vnd(BigDecimal.valueOf(amount)),
                quantity,
                purchaseLimit);
    }
}
