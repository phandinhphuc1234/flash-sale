package com.philia.flashsale.product.config;

import com.philia.flashsale.product.application.port.in.BrowseCatalogUseCase;
import com.philia.flashsale.product.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.application.service.ProductCatalogQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductCatalogConfiguration {

    @Bean
    // Wire the application use case to its outbound port without making the use case depend on Spring.
    BrowseCatalogUseCase browseCatalogUseCase(LoadCatalogPort loadCatalogPort) {
        return new ProductCatalogQueryService(loadCatalogPort);
    }
}
