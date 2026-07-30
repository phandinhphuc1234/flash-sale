package com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.mapper;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignItemJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import java.math.BigDecimal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/** Maps the Campaign domain model to the Campaign-owned JPA representation. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CampaignPersistenceMapper {

    @Mapping(target = "id", expression = "java(source.id())")
    @Mapping(target = "code", expression = "java(source.code())")
    @Mapping(target = "name", expression = "java(source.name())")
    @Mapping(target = "status", expression = "java(source.status())")
    @Mapping(target = "startAt", expression = "java(source.startAt())")
    @Mapping(target = "endAt", expression = "java(source.endAt())")
    @Mapping(target = "scheduledAt", expression = "java(source.scheduledAt())")
    @Mapping(target = "activatedAt", expression = "java(source.activatedAt())")
    @Mapping(target = "endedAt", expression = "java(source.endedAt())")
    @Mapping(target = "createdBy", expression = "java(source.createdBy())")
    @Mapping(target = "updatedBy", expression = "java(source.updatedBy())")
    @Mapping(target = "createdAt", expression = "java(source.createdAt())")
    @Mapping(target = "updatedAt", expression = "java(source.updatedAt())")
    @Mapping(target = "item", expression = "java(toEntity(source.item()))")
    @Mapping(target = "version", ignore = true)
    CampaignJpaEntity toEntity(Campaign source);

    @Mapping(target = "code", expression = "java(source.code())")
    @Mapping(target = "name", expression = "java(source.name())")
    @Mapping(target = "status", expression = "java(source.status())")
    @Mapping(target = "startAt", expression = "java(source.startAt())")
    @Mapping(target = "endAt", expression = "java(source.endAt())")
    @Mapping(target = "scheduledAt", expression = "java(source.scheduledAt())")
    @Mapping(target = "activatedAt", expression = "java(source.activatedAt())")
    @Mapping(target = "endedAt", expression = "java(source.endedAt())")
    @Mapping(target = "updatedBy", expression = "java(source.updatedBy())")
    @Mapping(target = "updatedAt", expression = "java(source.updatedAt())")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "item", ignore = true)
    void updateEntity(Campaign source, @MappingTarget CampaignJpaEntity target);

    @Mapping(target = "id", expression = "java(source.id())")
    @Mapping(target = "productId", expression = "java(source.productId())")
    @Mapping(target = "variantId", expression = "java(source.variantId())")
    @Mapping(target = "inventoryAllocationId", expression = "java(source.inventoryAllocationId())")
    @Mapping(target = "variantSkuSnapshot", expression = "java(source.variantSkuSnapshot())")
    @Mapping(target = "requestedQuantity", expression = "java(source.requestedQuantity())")
    @Mapping(target = "allocatedQuantity", expression = "java(source.allocatedQuantity())")
    @Mapping(target = "purchaseLimitPerUser", expression = "java(source.purchaseLimitPerUser())")
    @Mapping(target = "campaign", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "basePriceSnapshot", expression = "java(toAmount(source.basePriceSnapshot()))")
    @Mapping(target = "currencySnapshot", expression = "java(toCurrency(source.basePriceSnapshot()))")
    @Mapping(target = "campaignPrice", expression = "java(source.campaignPrice().amount())")
    CampaignItemJpaEntity toEntity(CampaignItem source);

    @Mapping(target = "productId", expression = "java(source.productId())")
    @Mapping(target = "variantId", expression = "java(source.variantId())")
    @Mapping(target = "inventoryAllocationId", expression = "java(source.inventoryAllocationId())")
    @Mapping(target = "variantSkuSnapshot", expression = "java(source.variantSkuSnapshot())")
    @Mapping(target = "requestedQuantity", expression = "java(source.requestedQuantity())")
    @Mapping(target = "allocatedQuantity", expression = "java(source.allocatedQuantity())")
    @Mapping(target = "purchaseLimitPerUser", expression = "java(source.purchaseLimitPerUser())")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "campaign", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "basePriceSnapshot", expression = "java(toAmount(source.basePriceSnapshot()))")
    @Mapping(target = "currencySnapshot", expression = "java(toCurrency(source.basePriceSnapshot()))")
    @Mapping(target = "campaignPrice", expression = "java(source.campaignPrice().amount())")
    void updateEntity(CampaignItem source, @MappingTarget CampaignItemJpaEntity target);

    default Campaign toDomain(CampaignJpaEntity source) {
        if (source == null) {
            return null;
        }
        return Campaign.rehydrate(
                source.getId(),
                source.getCode(),
                source.getName(),
                source.getStartAt(),
                source.getEndAt(),
                source.getStatus(),
                source.getScheduledAt(),
                source.getActivatedAt(),
                source.getEndedAt(),
                source.getVersion() == null ? 0L : source.getVersion(),
                source.getCreatedBy(),
                source.getUpdatedBy(),
                source.getCreatedAt(),
                source.getUpdatedAt(),
                toDomain(source.getItem()));
    }

    default CampaignItem toDomain(CampaignItemJpaEntity source) {
        if (source == null) {
            return null;
        }
        return CampaignItem.rehydrate(
                source.getId(),
                source.getProductId(),
                source.getVariantId(),
                source.getInventoryAllocationId(),
                source.getVariantSkuSnapshot(),
                toMoney(source.getBasePriceSnapshot(), source.getCurrencySnapshot()),
                CampaignMoney.vnd(source.getCampaignPrice()),
                source.getRequestedQuantity(),
                source.getAllocatedQuantity(),
                source.getPurchaseLimitPerUser());
    }

    default BigDecimal toAmount(CampaignMoney money) {
        return money == null ? null : money.amount();
    }

    default String toCurrency(CampaignMoney money) {
        return money == null ? null : money.currency();
    }

    default CampaignMoney toMoney(BigDecimal amount, String currency) {
        return amount == null ? null : new CampaignMoney(amount, currency);
    }
}
