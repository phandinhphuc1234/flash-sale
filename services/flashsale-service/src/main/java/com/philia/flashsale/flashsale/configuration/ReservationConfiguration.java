package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.reservation.adapter.out.redis.AtomicReservationRedisAdapter;
import com.philia.flashsale.flashsale.reservation.application.port.in.ReserveCampaignQuotaUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadCampaignEndPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationFingerprintService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationIdentityFactory;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationSubmissionService;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReserveCampaignQuotaService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Composition root for the public admission and durable acceptance path. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(StringRedisTemplate.class)
public class ReservationConfiguration {

    @Bean
    AtomicReservationRedisAdapter atomicReservationRedisAdapter(StringRedisTemplate redis) {
        return new AtomicReservationRedisAdapter(redis);
    }

    @Bean
    ReservationFingerprintService reservationFingerprintService() {
        return new ReservationFingerprintService();
    }

    @Bean
    ReservationIdentityFactory reservationIdentityFactory() {
        return new ReservationIdentityFactory();
    }

    @Bean
    Clock flashSaleClock() {
        return Clock.systemUTC();
    }

    @Bean
    ReserveCampaignQuotaUseCase reserveCampaignQuotaUseCase(
            AtomicReservationRedisAdapter atomicReservation,
            ReservationFingerprintService fingerprints,
            ReservationIdentityFactory identities,
            Clock clock,
            FlashSaleProperties properties) {
        return new ReserveCampaignQuotaService(atomicReservation, fingerprints, identities, clock,
                properties.ttl(), properties.idempotencyRetentionAfterCampaignEnd());
    }

    @Bean
    @ConditionalOnBean(PersistAcceptedPurchasePort.class)
        ReservationSubmissionService reservationSubmissionService(
            ReserveCampaignQuotaUseCase admission,
            LoadCampaignEndPort campaignEnds,
            PersistAcceptedPurchasePort durableAcceptance,
            Clock clock) {
        return new ReservationSubmissionService(admission, campaignEnds, durableAcceptance, clock);
    }
}
