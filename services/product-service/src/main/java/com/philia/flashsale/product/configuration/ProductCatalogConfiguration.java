package com.philia.flashsale.product.configuration;

import com.philia.flashsale.product.catalog.application.port.in.BrowseCatalogUseCase;
import com.philia.flashsale.product.catalog.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.catalog.application.service.ProductCatalogQueryService;
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
