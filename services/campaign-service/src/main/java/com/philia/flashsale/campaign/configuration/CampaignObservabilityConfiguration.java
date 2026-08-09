package com.philia.flashsale.campaign.configuration;

import com.philia.flashsale.campaign.observability.CampaignObservability;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition-root wiring for Campaign semantic metrics and trace context helpers. */
@Configuration
public class CampaignObservabilityConfiguration {

    @Bean
    CampaignObservability campaignObservability(ObjectProvider<MeterRegistry> registries) {
        // Actuator supplies the real registry in production; a no-op fallback keeps minimal
        // context tests and non-metrics profiles from failing during bean construction.
        MeterRegistry registry = registries.getIfAvailable(SimpleMeterRegistry::new);
        return new CampaignObservability(registry);
    }
}
