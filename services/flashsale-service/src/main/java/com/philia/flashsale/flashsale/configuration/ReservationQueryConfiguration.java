package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.port.in.GetOwnedReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadOwnedReservationPort;
import com.philia.flashsale.flashsale.reservation.application.usecase.GetOwnedReservationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for durable owner-scoped reservation reads. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationQueryConfiguration {

    @Bean
    GetOwnedReservationUseCase getOwnedReservationUseCase(LoadOwnedReservationPort reservations) {
        return new GetOwnedReservationService(reservations);
    }
}
