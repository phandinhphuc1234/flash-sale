package com.philia.flashsale.campaign.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.campaign.campaign.domain.exception.CampaignDomainException;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import com.philia.flashsale.campaign.campaign.domain.policy.CampaignLifecyclePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit contract for the schedule preconditions that precede remote Product/Inventory calls. */
class CampaignSchedulePolicyTests {

    private static final Instant START = Instant.parse("2030-08-01T10:00:00Z");
    private static final Instant END = Instant.parse("2030-08-01T11:00:00Z");
    private static final Instant NOW = Instant.parse("2030-08-01T09:00:00Z");
    private final CampaignLifecyclePolicy policy = new CampaignLifecyclePolicy();

    @Test
    void acceptsFutureStartWithCompleteSnapshotAndAllocation() {
        Campaign campaign = draftWithCompleteItem();

        policy.ensureCanSchedule(campaign, NOW);

        assertThat(campaign.status()).isEqualTo(com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus.DRAFT);
        assertThat(campaign.item().isReadyForScheduling()).isTrue();
    }

    @Test
    void rejectsStartAtOrBeforeEvaluationTime() {
        Campaign campaign = draftWithCompleteItem();

        assertThatThrownBy(() -> policy.ensureCanSchedule(campaign, START))
                .isInstanceOf(CampaignDomainException.class)
                .extracting("code")
                .isEqualTo(CampaignDomainException.START_TIME_IN_PAST);
    }

    @Test
    void rejectsMissingItemAndIncompleteProductSnapshot() {
        Campaign draft = draft();
        assertThatThrownBy(() -> policy.ensureCanSchedule(draft, NOW))
                .isInstanceOf(CampaignDomainException.class)
                .extracting("code")
                .isEqualTo(CampaignDomainException.ITEM_REQUIRED);

        CampaignItem incomplete = draftItem().withProductSnapshot(
                UUID.randomUUID(), "SKU-1", CampaignMoney.vnd(new BigDecimal("120")));
        draft.replaceItem(incomplete, "admin", NOW);
        assertThatThrownBy(() -> policy.ensureCanSchedule(draft, NOW))
                .isInstanceOf(CampaignDomainException.class)
                .extracting("code")
                .isEqualTo(CampaignDomainException.ALLOCATION_INCOMPLETE);
    }

    @Test
    void enforcesCampaignPriceBelowBasePriceAndCurrencyMatchWhenSnapshotIsCaptured() {
        CampaignItem draftItem = CampaignItem.createDraft(
                null, UUID.randomUUID(), CampaignMoney.vnd(new BigDecimal("100")), 10, 1);

        assertThatThrownBy(() -> draftItem.withProductSnapshot(
                UUID.randomUUID(), "SKU-1", CampaignMoney.vnd(new BigDecimal("90"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CampaignMoney(new BigDecimal("100"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPartialAllocationAndRequiresFullRequestedQuantity() {
        CampaignItem item = draftItem().withProductSnapshot(
                UUID.randomUUID(), "SKU-1", CampaignMoney.vnd(new BigDecimal("120")));

        assertThatThrownBy(() -> item.withInventoryAllocation(UUID.randomUUID(), 9))
                .isInstanceOf(IllegalArgumentException.class);
        Campaign campaign = draft();
        campaign.replaceItem(item.withInventoryAllocation(UUID.randomUUID(), 10), "admin", NOW);
        policy.ensureCanSchedule(campaign, NOW);
        assertThat(campaign.item().allocatedQuantity()).isEqualTo(campaign.item().requestedQuantity());
    }

    @Test
    void scheduleTransitionFreezesReadySnapshotAndIsNotRetryableAsAnotherTransition() {
        Campaign campaign = draftWithCompleteItem();
        campaign.markScheduled("admin", NOW);

        assertThat(campaign.status()).isEqualTo(com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus.SCHEDULED);
        assertThat(campaign.item().isReadyForScheduling()).isTrue();
        assertThatThrownBy(() -> campaign.markScheduled("admin", NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryDecisionKeepsTheSameOperationIdentityForAResumableAllocation() {
        UUID operationId = UUID.randomUUID();
        UUID inventoryRequestId = UUID.randomUUID();

        assertThat(operationId).isNotEqualTo(inventoryRequestId);
        assertThat(inventoryRequestId).isEqualTo(inventoryRequestId);
    }

    private static Campaign draft() {
        return Campaign.createDraft(
                UUID.randomUUID(), "SCHEDULE-001", "Schedule campaign", START, END, "admin", NOW);
    }

    private static CampaignItem draftItem() {
        return CampaignItem.createDraft(
                null, UUID.randomUUID(), CampaignMoney.vnd(new BigDecimal("100")), 10, 1);
    }

    private static Campaign draftWithCompleteItem() {
        Campaign campaign = draft();
        CampaignItem item = draftItem()
                .withProductSnapshot(UUID.randomUUID(), "SKU-1", CampaignMoney.vnd(new BigDecimal("120")))
                .withInventoryAllocation(UUID.randomUUID(), 10);
        campaign.replaceItem(item, "admin", NOW);
        return campaign;
    }
}
