package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignItemJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository.CampaignJpaRepository;
import com.philia.flashsale.campaign.campaign.application.query.PublicCampaignFilter;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class PublicCampaignPersistenceAdapterTests {

    @Test
    void liveDiscoveryUsesTimeBoundsAndStableOrdering() {
        CampaignJpaRepository repository = mock(CampaignJpaRepository.class);
        Instant now = Instant.parse("2026-10-09T10:30:00Z");
        CampaignJpaEntity entity = entity();
        when(repository.findByStatusInAndStartAtLessThanEqualAndEndAtAfter(
                any(), eq(now), eq(now), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        var adapter = new PublicCampaignPersistenceAdapter(repository);

        var result = adapter.loadVisible(PublicCampaignFilter.LIVE, now, 0, 12);

        assertThat(result.data()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.name()).isEqualTo("Phone weekend");
            assertThat(snapshot.variantSku()).isEqualTo("PHONE-BLACK");
        });
        verify(repository).findByStatusInAndStartAtLessThanEqualAndEndAtAfter(
                any(), eq(now), eq(now), org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getSort().getOrderFor("startAt") != null
                                && pageable.getSort().getOrderFor("id") != null));
    }

    private CampaignJpaEntity entity() {
        CampaignJpaEntity campaign = new CampaignJpaEntity();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Phone weekend");
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setStartAt(Instant.parse("2026-10-09T10:00:00Z"));
        campaign.setEndAt(Instant.parse("2026-10-09T11:00:00Z"));
        CampaignItemJpaEntity item = new CampaignItemJpaEntity();
        item.setProductId(UUID.randomUUID());
        item.setVariantId(UUID.randomUUID());
        item.setVariantSkuSnapshot("PHONE-BLACK");
        item.setBasePriceSnapshot(new BigDecimal("229000"));
        item.setCampaignPrice(new BigDecimal("179000"));
        item.setCurrencySnapshot("VND");
        item.setPurchaseLimitPerUser(1);
        campaign.attachItem(item);
        return campaign;
    }
}
