package com.philia.flashsale.product.configuration;

import com.philia.flashsale.product.campaignvalidation.application.port.in.ValidateCampaignVariantUseCase;
import com.philia.flashsale.product.campaignvalidation.application.port.out.LoadCampaignVariantPort;
import com.philia.flashsale.product.campaignvalidation.application.usecase.ProductCampaignValidationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductCampaignValidationConfiguration {
    @Bean
    ValidateCampaignVariantUseCase validateCampaignVariantUseCase(
            LoadCampaignVariantPort variants, Clock productAdminClock) {
        return new ProductCampaignValidationService(variants, productAdminClock);
    }
}
