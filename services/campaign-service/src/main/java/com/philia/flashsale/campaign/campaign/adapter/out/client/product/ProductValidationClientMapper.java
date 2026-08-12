package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

import com.philia.flashsale.campaign.campaign.application.result.ValidatedCampaignVariant;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/** Maps Product wire data into the caller-owned Campaign application result. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductValidationClientMapper {

    ValidatedCampaignVariant toResult(ProductValidationResponse response);
}
