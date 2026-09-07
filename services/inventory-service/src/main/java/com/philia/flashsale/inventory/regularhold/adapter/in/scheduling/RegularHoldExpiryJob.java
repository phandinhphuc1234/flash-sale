package com.philia.flashsale.inventory.regularhold.adapter.in.scheduling;

import com.philia.flashsale.inventory.regularhold.application.port.in.ExpireDueRegularStockHoldsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Disabled-by-default scheduler; expiry transactions never perform Kafka I/O. */
@Component
@ConditionalOnProperty(name = "flashsale.inventory.regular-hold.expiry-enabled", havingValue = "true")
public class RegularHoldExpiryJob {
    private final ExpireDueRegularStockHoldsUseCase expiry;

    public RegularHoldExpiryJob(ExpireDueRegularStockHoldsUseCase expiry) {
        this.expiry = expiry;
    }

    @Scheduled(fixedDelayString = "${flashsale.inventory.regular-hold.expiry-scan-delay:1s}")
    public void expireDue() {
        expiry.expireDue();
    }
}
