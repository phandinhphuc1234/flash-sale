package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignItemJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository.CampaignJpaRepository;
import com.philia.flashsale.campaign.campaign.application.port.out.LoadPublicCampaignsPort;
import com.philia.flashsale.campaign.campaign.application.query.PublicCampaignFilter;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignPage;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignSnapshot;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Campaign-owned persistence adapter for bounded public discovery. */
@Component
@Transactional(readOnly = true)
public class PublicCampaignPersistenceAdapter implements LoadPublicCampaignsPort {

    private static final List<CampaignStatus> DISCOVERABLE =
            List.of(CampaignStatus.SCHEDULED, CampaignStatus.ACTIVE);
    private static final Sort PUBLIC_ORDER = Sort.by(
            Sort.Order.asc("startAt"), Sort.Order.asc("id"));

    private final CampaignJpaRepository repository;

    public PublicCampaignPersistenceAdapter(CampaignJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PublicCampaignPage<PublicCampaignSnapshot> loadVisible(
            PublicCampaignFilter filter, Instant now, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, PUBLIC_ORDER);
        Page<CampaignJpaEntity> result = switch (filter) {
            case ALL -> repository.findByStatusInAndEndAtAfter(DISCOVERABLE, now, pageable);
            case LIVE -> repository.findByStatusInAndStartAtLessThanEqualAndEndAtAfter(
                    DISCOVERABLE, now, now, pageable);
            case UPCOMING -> repository.findByStatusInAndStartAtAfterAndEndAtAfter(
                    DISCOVERABLE, now, now, pageable);
        };
        return new PublicCampaignPage<>(
                result.getContent().stream().map(this::toSnapshot).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Override
    public Optional<PublicCampaignSnapshot> loadPublicDetail(UUID campaignId) {
        return repository.findDetailedById(campaignId).map(this::toSnapshot);
    }

    private PublicCampaignSnapshot toSnapshot(CampaignJpaEntity campaign) {
        CampaignItemJpaEntity item = campaign.getItem();
        return new PublicCampaignSnapshot(
                campaign.getId(),
                campaign.getName(),
                campaign.getStatus(),
                campaign.getStartAt(),
                campaign.getEndAt(),
                item == null ? null : item.getProductId(),
                item == null ? null : item.getVariantId(),
                item == null ? null : item.getVariantSkuSnapshot(),
                item == null ? null : item.getBasePriceSnapshot(),
                item == null ? null : item.getCampaignPrice(),
                item == null ? null : item.getCurrencySnapshot(),
                item == null ? 0 : item.getPurchaseLimitPerUser());
    }
}
