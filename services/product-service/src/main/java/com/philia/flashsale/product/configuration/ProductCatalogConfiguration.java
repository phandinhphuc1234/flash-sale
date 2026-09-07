package com.philia.flashsale.product.configuration;

import com.philia.flashsale.product.catalog.application.port.in.BrowseCatalogUseCase;
import com.philia.flashsale.product.catalog.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.catalog.application.port.in.LookupVariantDisplaysUseCase;
import com.philia.flashsale.product.catalog.application.service.ProductCatalogQueryService;
import com.philia.flashsale.product.catalog.application.service.ProductVariantDisplayQueryService;
import com.philia.flashsale.product.catalogquery.application.PurchaseQuoteService;
import com.philia.flashsale.product.catalogquery.application.port.in.LookupPurchaseQuotesUseCase;
import com.philia.flashsale.product.catalogquery.application.port.out.LoadPurchaseQuotePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductCatalogConfiguration {

    @Bean
    // Wire the application use case to its outbound port without making the use case depend on Spring.
    BrowseCatalogUseCase browseCatalogUseCase(LoadCatalogPort loadCatalogPort) {
        return new ProductCatalogQueryService(loadCatalogPort);
    }

    @Bean
    LookupVariantDisplaysUseCase lookupVariantDisplaysUseCase(LoadCatalogPort loadCatalogPort) {
        return new ProductVariantDisplayQueryService(loadCatalogPort);
    }

    @Bean
    LookupPurchaseQuotesUseCase lookupPurchaseQuotesUseCase(LoadPurchaseQuotePort loadPurchaseQuotePort) {
        return new PurchaseQuoteService(loadPurchaseQuotePort);
    }
}
