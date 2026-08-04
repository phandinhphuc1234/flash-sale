package com.philia.flashsale.campaign.campaign.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.campaign.campaign.domain.exception.CampaignDomainException;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import com.philia.flashsale.campaign.campaign.domain.policy.CampaignLifecyclePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignLifecyclePolicyTests {

    private static final Instant START = Instant.parse("2030-08-01T10:00:00Z");
    private static final Instant END = Instant.parse("2030-08-01T11:00:00Z");
    private static final Instant ACTIVATE_AT = START;
    private final CampaignLifecyclePolicy policy = new CampaignLifecyclePolicy();

    @Test
    void activationIsAllowedAtStartButNotBeforeStartOrAtEnd() {
        Campaign scheduled = scheduledCampaign();

        policy.ensureCanActivate(scheduled, ACTIVATE_AT);

        assertThatThrownBy(() -> policy.ensureCanActivate(scheduled, START.minusNanos(1)))
                .isInstanceOf(CampaignDomainException.class)
                .hasMessageContaining("activation");
        assertThatThrownBy(() -> policy.ensureCanActivate(scheduled, END))
                .isInstanceOf(CampaignDomainException.class)
                .hasMessageContaining("activation");
    }

    @Test
    void activationRequiresScheduledStatusAndCompleteSnapshotAndAllocation() {
        Campaign draft = Campaign.createDraft(
                UUID.randomUUID(), "LIFECYCLE-DRAFT", "Lifecycle", START, END, "admin", START.minusSeconds(10));
        assertThatThrownBy(() -> policy.ensureCanActivate(draft, ACTIVATE_AT))
                .isInstanceOf(CampaignDomainException.class);

        Campaign incomplete = Campaign.rehydrate(
                UUID.randomUUID(), "LIFECYCLE-INCOMPLETE", "Lifecycle", START, END,
                CampaignStatus.SCHEDULED, START.minusSeconds(10), null, null, 1,
                "admin", "admin", START.minusSeconds(20), START.minusSeconds(10),
                CampaignItem.rehydrate(
                        UUID.randomUUID(), null, UUID.randomUUID(), null, null, null,
                        CampaignMoney.vnd(new BigDecimal("90")), 10, 0, 1));
        assertThatThrownBy(() -> policy.ensureCanActivate(incomplete, ACTIVATE_AT))
                .isInstanceOf(CampaignDomainException.class);
    }

    @Test
    void lifecycleIsMonotonicAndEndedIsTerminal() {
        Campaign campaign = scheduledCampaign();
        campaign.markActive("scheduler", ACTIVATE_AT);
        policy.ensureCanEnd(campaign, END);
        campaign.markEnded("scheduler", END);

        assertThatThrownBy(() -> campaign.markActive("scheduler", END.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy.ensureCanEnd(campaign, END.plusSeconds(1)))
                .isInstanceOf(CampaignDomainException.class);
    }

    @Test
    void endingRequiresTheEndBoundary() {
        Campaign campaign = scheduledCampaign();
        campaign.markActive("scheduler", ACTIVATE_AT);

        assertThatThrownBy(() -> policy.ensureCanEnd(campaign, END.minusNanos(1)))
                .isInstanceOf(CampaignDomainException.class)
                .hasMessageContaining("end");
    }

    private static Campaign scheduledCampaign() {
        return Campaign.rehydrate(
                UUID.randomUUID(), "LIFECYCLE-READY", "Lifecycle", START, END,
                CampaignStatus.SCHEDULED, START.minusSeconds(10), null, null, 1,
                "admin", "admin", START.minusSeconds(20), START.minusSeconds(10),
                CampaignItem.rehydrate(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        "SKU-READY", CampaignMoney.vnd(new BigDecimal("100")),
                        CampaignMoney.vnd(new BigDecimal("90")), 10, 10, 1));
    }
}
